import json
import inspect 

from types import *

RESERVED_NAMES = {"__builtins__", "__cached__", "__doc__", "__file__", "__loader__",
"__name__", "__package__", "__warningregistry__"}

SUPPORTED_TYPES = {type(None), type(True), type(0), type(0.0), type("Shu"), 
type(u"Shu"), type((0,)), type([0]), type({"a" : 0}), FunctionType, LambdaType, ModuleType}

def get_globals(frame):
	return frame.f_globals

def get_param_names(func):
	arg_spec = inspect.getargspec(func)

	param_names = arg_spec[0]
	if arg_spec[1] != None:
		param_names.extend(arg_spec[1])
	if arg_spec[2] != None:
		param_names.extend(arg_spec[2])

	return param_names

def is_valid(name, val):
	is_valid_val = False
	source_file = ""

	try:
		source_file = inspect.getsourcefile(val)
		is_valid_val = source_file != "snappy.py" and source_file == "<string>" and type(val) in SUPPORTED_TYPES 
	except TypeError:
		is_valid_val = type(val) in SUPPORTED_TYPES

	return name not in RESERVED_NAMES and is_valid_val


def get_bindings(frame):
	local_bindings, nonlocal_vars = frame.f_locals.items(), frame.f_code.co_freevars

	bindings = {}
	bindings["locals"] = {name : val for name, val in local_bindings \
	if name not in nonlocal_vars and is_valid(name, val)}
	bindings["nonlocals"] = {name : val for name, val in local_bindings \
	if name in nonlocal_vars and is_valid(name, val)}
	
	return bindings

def parent_pointers_to_json(func_to_parent):
	new_dict = {}

	for func, parent_info in func_to_parent.items():
		new_dict[id(func)] = None if parent_info == None else {"id" : parent_info[0], "locals" : parent_info[1], "hash" : id(parent_info[2])}

	return new_dict 

def snapshots_to_json(snapshots):
	for snapshot in snapshots:
		snapshot["locals"] = {name : object_to_dict(val) for name, val in snapshot["locals"].items()}
		snapshot["nonlocals"] = {name : object_to_dict(val) for name, val in snapshot["nonlocals"].items()}

		if "ret_val" in snapshot:
			snapshot["ret_val"] = object_to_dict(snapshot["ret_val"])

	return snapshots

def object_to_dict(obj):
	if inspect.isfunction(obj):
		return function_to_dict(obj)
	elif inspect.ismodule(obj):
		return module_to_dict(obj)
	elif type(obj) in {set, list, tuple, dict}:
		return seq_to_dict(obj)
	else:
		return obj_to_dict(obj)

def obj_to_dict(obj):
	return {"type" : "object", "hash" : id(obj), "attrs" : {"value" : obj}}

def seq_to_dict(obj):
	my_dict = {}
	my_dict["hash"] = id(obj)

	if type(obj) == dict:
		my_dict["type"] = "dict"
		my_dict["items"] = [(object_to_dict(k), object_to_dict(v)) for k, v in obj.items()]
	elif type(obj) == list:
		my_dict["type"] = "seq"
		my_dict["items"] = [object_to_dict(elem) for elem in obj]
	elif type(obj) == tuple:
		my_dict["type"] = "seq"
		my_dict["items"] = [object_to_dict(elem) for elem in obj]

	return my_dict

def module_to_dict(module):
	return {"type" : "module", "hash" : id(module), "attrs" : {"name" : module.__name__}}	

def function_to_dict(func):
	return {"type" : "function", "hash" : id(func.__code__), "attrs" : {"name" : func.__name__, "params" : get_param_names(func)}}

if __name__ == "__main__":
	print(json.dumps(seq_to_dict({"a" : lambda : 1})))