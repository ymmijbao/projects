import ast
import bdb
import collections
import inspect
import util

import pprint

class Snappy(bdb.Bdb):
	class Preprocessor(ast.NodeTransformer):
		def __init__(self):
			ast.NodeTransformer.__init__(self)

			self.lines_of_code = []

			self.__prev_clause = collections.defaultdict(lambda : -1)
			self.if_nodes = {}
			self.while_nodes = {}

		def run(self, user_script):
			self.lines_of_code = user_script.split("\n")
			self.visit(ast.parse(user_script))
		
		def __adjust_end(self, end):
			real_end = end

			if end in self.if_nodes:
				real_end = self.if_nodes[end]["end"]
			elif end in self.while_nodes:
				real_end = self.while_nodes[end]["end"]

			return real_end

		def visit_If(self, node):
			for child in ast.iter_child_nodes(node):
				self.visit(child)

			next = -1 if len(node.orelse) == 0 else node.orelse[0].lineno
			if next != -1:
				self.__prev_clause[node.orelse[0].lineno] = node.lineno

			self.if_nodes[node.lineno] = {"end" : self.__adjust_end(node.body[-1].lineno), 
			"prev" : self.__prev_clause[node.lineno], "next" : next, 
			"cond" : self.lines_of_code[node.lineno - 1].strip()}	

			if (len(node.orelse) == 1 and not isinstance(node.orelse[0], ast.If)) or len(node.orelse) > 1:
				self.if_nodes[node.orelse[0].lineno - 1] = {"end" : self.__adjust_end(node.orelse[-1].lineno),
				"prev" : self.__prev_clause[node.orelse[0].lineno], "next" : -1}

			return node

		def visit_While(self, node):
			for child in ast.iter_child_nodes(node):
					self.visit(child)

			print(node.lineno)

			self.while_nodes[node.lineno] = {"end" : self.__adjust_end(node.body[-1].lineno), 
			"cond" : self.lines_of_code[node.lineno - 1].strip()}

			return node

		def visit_Call(self, node):
			return node

	def __init__(self):
		bdb.Bdb.__init__(self)

		self.preprocessor = Snappy.Preprocessor()
		
		self.snapshots = []
		
		self.__cur_frame_id = -1
		self.__frame_ids = [-1]

		self.__func_to_args = collections.defaultdict(set)
		self.func_to_parent = collections.defaultdict(lambda : None)
		
	def preprocess(self, userScript):
		self.preprocessor.run(userScript)

	def __set_func_to_args(self, val, frame_id, frame_code):
		if inspect.isfunction(val):
			self.__func_to_args[frame_id, frame_code].add(val.__code__)
		elif type(val) in {set, list, tuple}:
			for elem in val:
				self.__set_func_to_args(elem, frame_id, frame_code)

	def user_call(self, frame, arg_list):
		if frame.f_code.co_filename == "<string>":
			self.__cur_frame_id += 1
			self.__frame_ids.append(self.__cur_frame_id)
			for val in frame.f_locals.values():
				self.__set_func_to_args(val, self.__cur_frame_id, frame.f_code)
			
			if frame.f_code.co_name == "<lambda>":
				self.__set_parent(frame.f_code, self.__frame_ids[-2], frame.f_back)

	def user_line(self, frame):
		if frame.f_code.co_filename == "<string>":
			self.snapshots.append(self.__snapshot(frame))

	def user_return(self, frame, ret_val):
		if frame.f_code.co_filename == "<string>":
			snapshot = self.__snapshot(frame)
			snapshot["ret_val"] = ret_val
			self.snapshots.append(snapshot)
	

			frame_id = self.__get_frame_id()
			if inspect.isfunction(ret_val):
				self.__set_parent(ret_val.__code__, frame_id, frame)
			elif type(ret_val) in {list, set, tuple}:
				self.__set_parents_of_seq(ret_val, frame_id, frame)

			if (frame_id, frame.f_code) in self.__func_to_args:
				del self.__func_to_args[frame_id, frame.f_code]

			self.__frame_ids.pop()

	def __get_frame_id(self):
		return self.__frame_ids[-1]

	def __set_parent(self, func_code, parent_id, parent_frame):
		cur_parent_info = self.func_to_parent[func_code]
		parent_vars = list(util.get_bindings(parent_frame)["locals"].keys())

		if cur_parent_info == None:
			if func_code in self.__func_to_args[parent_id, parent_frame.f_code]:
				caller_frame = parent_frame.f_back
				caller_id = self.__frame_ids[-2]
				caller_vars = list(util.get_bindings(caller_frame)["locals"].keys())

				self.func_to_parent[func_code] = (caller_id, dict(zip(caller_vars, [True for i in range(len(caller_vars))])), caller_frame.f_code)
			else:
				self.func_to_parent[func_code] = (parent_id, dict(zip(parent_vars, [True for i in range(len(parent_vars))])), parent_frame.f_code)
		elif cur_parent_info[0] == parent_id:
			self.func_to_parent[func_code] = (parent_id, dict(zip(parent_vars, [True for i in range(len(parent_vars))])), parent_frame.f_code)

	def __set_parents_of_seq(self, seq, frame_id, frame):
		for val in seq:
		    if inspect.isfunction(val):
		    	self.__set_parent(val.__code__, frame_id, frame)
		    elif type(val) in {list, set, tuple}:
		    	self.__set_parents_of_seq(val, frame_id, frame)

	def __snapshot(self, frame):
		frame_info = inspect.getframeinfo(frame)
		frame_id = self.__get_frame_id()
		bindings = util.get_bindings(frame)
	
		for name, val in bindings["locals"].items():
			if inspect.isfunction(val):
				self.__set_parent(val.__code__, frame_id, frame)
			elif type(val) in {list, set, tuple}:
				self.__set_parents_of_seq(val, frame_id, frame)


		parent_id, parent_info = -1, self.func_to_parent[frame.f_code]
		if parent_info != None:
			parent_id = parent_info[0]		

		return {"hash" : id(frame.f_code), "lineno" : frame_info[1], "id" : frame_id, "parent_id" : parent_id,  
		"func_name" : frame_info[2], "locals" : bindings["locals"], "nonlocals" : bindings["nonlocals"]}
		

if __name__ == "__main__":
	user_script = open("../tests/test_script2.txt").read()

	snap = Snappy()
	snap.preprocess(user_script)
	snap.run(user_script)
	pprint.pprint(snap.snapshots)