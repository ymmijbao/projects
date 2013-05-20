var renderer = new Renderer();
var code_editor;
var breakpoints = {};

function start_rendering(data) {
	if (renderer.cur_lineno != -1) {
		var remove_handle = code_editor.getLineHandle(renderer.cur_lineno);
		code_editor.removeLineClass(remove_handle, null, "activeline");
	}
	renderer.clear();

	renderer.set_data(data);
	$("#next_button").click(function(event) {
		if (renderer.i < renderer.snapshots.length) {
			renderer.render_snapshot();
			renderer.i += 1;
		}
	});

	$("#cont_button").click(function(event) {
		while (renderer.i < renderer.snapshots.length) {
			var cur_lineno = renderer.snapshots[renderer.i].lineno;
			renderer.render_snapshot();
			renderer.i += 1;

			if (cur_lineno in breakpoints) {
				alert("Breakpoint at line " + cur_lineno + " reached!");
				remove_breakpoint(cur_lineno);
				break;
			}
		}
	});
}

function set_editor(editor) {
	code_editor = editor;
}

function set_breakpoint(lineno) {
	breakpoints[lineno] = true;
	var add_handle = code_editor.getLineHandle(lineno - 1);
	code_editor.addLineClass(add_handle, null, "breakpoint");
}

function remove_breakpoint(lineno) {
	delete breakpoints[lineno];
	var remove_handle = code_editor.getLineHandle(lineno - 1);
	code_editor.removeLineClass(remove_handle, null, "breakpoint");
}

function remove_all_breakpoints() {
	for (var breakpoint in breakpoints) {
		remove_breakpoint(breakpoint);
	}
}

function Renderer() {
	this.reset = function() {
		this.rendered_frames = {};
		this.i = 0;

		this.linenos_seen = {};
		this.frame_id_to_blocks = {};
		this.frame_id_to_whiles = {};

		this.cur_lineno = -1;
	}

	this.reset();

	this.set_data = function(data) {
		this.reset();

		this.snapshots = data.snapshots;
		this.pointers = data.pointers;
		this.if_nodes = data.if_nodes;
		this.while_nodes = data.while_nodes;
	}

	this.display_cond = function(control_info) {
		var prev_clauses = [];
		var next_clauses = [];
		var start_clause = this.if_nodes[control_info["start"]];

		var cur_clause = start_clause;
		while (cur_clause != null) {
		    prev_clauses.unshift(cur_clause["cond"]);
		    cur_clause = this.if_nodes[cur_clause["prev"]];
		}

		var next = start_clause["next"]; 
		while (next != -1) {
			cur_clause = this.if_nodes[next];
			if (cur_clause == null) {
				cur_clause = this.if_nodes[next - 1];
			}

			var cond = (cur_clause["cond"] == null) ? "else:" : cur_clause["cond"];
			next_clauses.push(cond);
			next = cur_clause["next"];
		}
	}

	this.display_control_flow = function(snapshot, frame_id) {
		var snapshot_blocks = this.frame_id_to_blocks[snapshot.id];
		if (this.is_end_of_block(snapshot)) {
			if ("ret_val" in snapshot) {
				for (var i in snapshot_blocks) {
					var block = snapshot_blocks[i];
					jsPlumb.detachAllConnections("" + block["id"]);
					$("#" + block["id"]).remove();
					delete this.linenos_seen["" + snapshot.id + block["start"] + block["type"]];
				}

				delete this.frame_id_to_blocks[snapshot.id];
				delete this.frame_id_to_whiles[snapshot.id];

				return;
			} else {
				var last_block = snapshot_blocks.pop();
				jsPlumb.detachAllConnections("" + last_block["id"]);
				$("#" + last_block["id"]).remove();
				if (last_block["type"] == "while") {
					this.frame_id_to_whiles[snapshot.id].pop();
				}

				delete this.linenos_seen["" + snapshot.id + last_block["start"] + last_block["type"]];
			}
		}

		var control_type = "";
		if (snapshot.lineno in this.if_nodes) {
			control_type = "if";
		} else if (snapshot.lineno in this.while_nodes) {
			control_type = "while";
		} 

		if (control_type != "") {
			var key = "" + snapshot.id + snapshot.lineno + control_type;
			if (!(key in this.linenos_seen)) {
				this.linenos_seen[key] = true;
				var control_nodes = (control_type == "if") ? this.if_nodes : this.while_nodes;
				var control_id = this.generate_random_id();
				var control_info = {"type" : control_type, "id" : control_id, "start" : snapshot.lineno, 
				"end" : control_nodes[snapshot.lineno]["end"]};

				if (control_type == "while") {
					control_info["iterations"] = [[snapshot.locals, snapshot.nonlocals]];
					if (snapshot.id in this.frame_id_to_whiles) {
						this.frame_id_to_whiles[snapshot.id].push(control_id);
					} else {
						this.frame_id_to_whiles[snapshot.id] = [control_id];
					}
				}			
			
				if (snapshot_blocks == null) {
					this.frame_id_to_blocks[snapshot.id] = [control_info];
				} else {
					snapshot_blocks.push(control_info);
				}

				if (control_type == "while") {
					this.construct_while(control_id, snapshot.id, frame_id, snapshot.hash, snapshot.lineno);
					this.draw_arrow(frame_id, control_id, "RightMiddle", "LeftMiddle", false);
				}
			} else if (snapshot_blocks != null && snapshot_blocks.length > 0) {
				for (var i = snapshot_blocks.length - 1; i > -1; i--) {
					var cur_block = snapshot_blocks[i];
					if (cur_block["type"] == "while") {
						cur_block["iterations"].push([snapshot.locals, snapshot.nonlocals]);
						return;
					}
				}
			}
		}
	}

	this.is_end_of_block = function(snapshot) {
		var snapshot_blocks = this.frame_id_to_blocks[snapshot.id];

		if (snapshot_blocks != null && snapshot_blocks.length > 0) {
			var last_block = snapshot_blocks[snapshot_blocks.length - 1];

			var returned_to_outer = false;
			var outer_block = snapshot_blocks[snapshot_blocks.length - 2];
			if (outer_block != null) {
				returned_to_outer = (snapshot.lineno == outer_block["start"]);
			}

			return "ret_val" in snapshot || snapshot.lineno > last_block["end"] || returned_to_outer; 
		}

		return false;
	}

	this.generate_random_id = function() {
		return Math.floor(Math.random() * 1000000000);
	}

	this.get_nonlocal_parent = function(hash, name) {
		var parent_info = this.pointers[hash];
		if (parent_info != null) {
			if (name in parent_info.locals) {
				return parent_info.id;
			} else {
				return this.get_nonlocal_parent(parent_info.hash, name);
			}
		}
	}

	this.render_snapshot = function() {
		var snapshot = this.snapshots[this.i];

		var frame_id = "";
		var func_name = "";
		var parent_id = "";

		if (snapshot.id == -1) {
			frame_id = "G";
			func_name = "module";
		} else {
			frame_id = "f" + snapshot.id;
			func_name = (snapshot.func_name == "<lambda>") ? "lambda" : snapshot.func_name;

			if (snapshot.parent_id == -1) {
				parent_id = "G";
			} else {
				parent_id = "f" + snapshot.parent_id;
			}
		}


		if (!(frame_id in this.rendered_frames)) {
			this.draw_frame(this.construct_empty_frame(frame_id, func_name, parent_id, false));
			this.rendered_frames[frame_id] = true;
		}

		this.display_locals(snapshot.locals, frame_id);
		this.display_nonlocals(snapshot.nonlocals, frame_id, snapshot.hash);

		if ("ret_val" in snapshot && frame_id != "G") {
			this.add_ret_val(frame_id, snapshot.ret_val);
		}

		this.display_control_flow(snapshot, frame_id);
		this.highlight_lineno(snapshot.lineno - 1);
	}


	this.highlight_lineno = function(lineno) {
		if (this.cur_lineno != -1) {
			var remove_handle = code_editor.getLineHandle(this.cur_lineno);
			code_editor.removeLineClass(remove_handle, null, "activeline");
		}

  		var add_handle = code_editor.getLineHandle(lineno);
	  	code_editor.addLineClass(add_handle, null, "activeline");	

	  	this.cur_lineno = lineno;
	} 

	this.display_locals = function(locals, frame_id) {
		for (var name in locals) {
			this.add_binding(frame_id, name, locals[name], -1);
		}
	}

	this.display_nonlocals = function(nonlocals, frame_id, hash) {
		for (var name in nonlocals) {
			var nonlocal_parent = this.get_nonlocal_parent(hash, name);
			this.add_binding(frame_id, name, nonlocals[name], nonlocal_parent);
			this.add_binding("f" + nonlocal_parent, name, nonlocals[name], -1);
		}
	}

	this.draw_frame = function(frame) {
		$("#stack").append(frame);
	}

	this.draw_object = function(obj, hash) {
		if($("#" + hash).length == 0) {
			$("#heap").append(obj);
		}
	}

	this.construct_empty_frame = function(frame_id, func_name, parent_id, is_rounded) {
		var frame_class = is_rounded ? "rounded_frame" : "normal_frame";
	
		var frame_header = "<table class='" + frame_class + "' border='0' id='" + frame_id + 
		"'><tr><th class='frame_header padded'>" + frame_id + "[" + func_name +"]" +
	 	"</th><th class='frame_header'>" + parent_id + "</th></tr></table>";

		return frame_header;
	}


	this.construct_function = function(name, params, parent_id, func_id) {
		name = (name == "<lambda>") ? "lambda" : name;
		var function_text = "def " + name + "(" + params.join(", ") + "):";
		
		var func_frame = "<table class='rounded_frame func_object' border='0' id='" +
    	func_id + "'><tr><td class='padded'/><td>" + parent_id + "</td></tr><tr><td>" + 
    	function_text + "</td></tr></table>";    

    	return func_frame;
	}

	this.construct_seq = function(seq) {
		var seq_text = "<table class='seq_object' id ='" + seq.hash + "'><tr>";
		var i = 0;

		for (i = 0; i < seq.items.length; i++) {
			var item = seq.items[i];
			seq_text += "<th class='seq_box' id='" + seq.hash + "" + i + "'>" + this.extract_value(item) + "</th>";
		}

		if ($('#' + seq.hash).length == 0) {
			$('#control').append(seq_text + "</tr></table>");
		}

		for (i = 0; i < seq.items.length; i++) {
			var item = seq.items[i];
			if (item.type == "function") {
				this.draw_arrow(seq.hash + "" + i, item.hash, "RightMiddle", "RightMiddle", false);
			}
		}
	}

	this.construct_while = function(id, snapshot_id, frame_id, hash, lineno) {
		var frame_header = "<table class='rounded_frame while_frame' border='0' id='" + id + 
		"'><tr><td class='padded'>" + frame_id + " LN:" + lineno + "</td><td id='" + id + "_iter'>0</td></tr>" + 
		"<tr><td><button id='" + id + "_prev" + "'>&lt</button></td>" + 
		"<td><button id='" + id + "_next" + "'>&gt</button></td></tr></table>";
	
		$("#control").append(frame_header);
		this.draw_arrow(id, id, "RightMiddle", "TopCenter", true);
		
		var i = -1;
		$("#" + id + "_prev").click(function(event) {
			i = i - 1;
			i = while_handler(id, snapshot_id, hash, i);
			$("#" + id + "_iter").empty().append(i);
		});

		$("#" + id + "_next").click(function(event) {
			i = i + 1;
			i = while_handler(id, snapshot_id, hash, i);
			$("#" + id + "_iter").empty().append(i);
		});
	}

	var while_handler = function(id, snapshot_id, hash, num_iter) {
		var snapshot_blocks = renderer.frame_id_to_blocks[snapshot_id];

		for (var i = snapshot_blocks.length - 1; i > -1; i--) {
			var cur_block = snapshot_blocks[i];
			if (cur_block["type"] == "while" && cur_block["id"] == id) {
				var iterations = cur_block["iterations"];
				if (num_iter > -1 && num_iter < iterations.length) {
					renderer.display_locals(iterations[num_iter][0], id);
					renderer.display_nonlocals(iterations[num_iter][1], id);

					return num_iter; 	
				} else if (num_iter >= iterations.length) {
					return num_iter - 1;
				} else {
					return 0;
				}
			}
		}	
	}

	this.extract_value = function(obj) {
		var attrs = obj.attrs;
		var obj_val = "";

		if (obj.type == "object") {
			obj_val = (attrs.value == null) ? "None" : attrs.value;
		} else if (obj.type == "function") {
			var function_parent = "";
			var parent_info = this.pointers[obj.hash];
			if (parent_info != null) {
				func_parent = (parent_info.id == -1) ? "G" : "f" + parent_info.id;
			}

			this.draw_object(this.construct_function(attrs.name, attrs.params, func_parent, obj.hash), obj.hash);
		} else if (obj.type == "module") {
			obj_val = "module object";
		} else if (obj.type == "seq") {
			this.construct_seq(obj);
		}

		return obj_val;
	}

	this.add_binding = function(frame_id, name, value, nonlocal_parent) {
		var value_id = frame_id + name;
		var binding_name = (nonlocal_parent != -1) ? name + "[f" + nonlocal_parent + "]" : name;
		var binding_class = (nonlocal_parent != -1) ? "nonlocal" : "local";
		var rendered_val = this.extract_value(value);

		if($("#" + value_id).length > 0) {
			$("#" + value_id).empty().append(rendered_val);
		} else {
			$("#" + frame_id).append("<tr class='" + binding_class +"'><td>" + binding_name + "</td><td id='" +
			value_id + "'>" + rendered_val + "</td></tr>");
		}

		if (value.type == "seq" || value.type == "function") {
			this.draw_arrow(value_id, value.hash, "RightMiddle", "LeftMiddle", false);
		}

		this.fix_arrows();
	}

	this.fix_arrows = function() {
		var connections = jsPlumb.getConnections();
		var source_and_target = [];
		for (var i = 0; i < connections.length; i++) {
			source_and_target.push([connections[i].sourceId, connections[i].targetId]);
		}

 	    jsPlumb.detachEveryConnection();
    	jsPlumb.deleteEveryEndpoint();

        for (var j = 0; j < source_and_target.length; j++) {
			var source_id = source_and_target[j][0];
        	var target_id = source_and_target[j][1];
        	var is_curved = (source_id == target_id) ? true : false;
        	var target_anc = (source_id == target_id) ? "TopCenter" : "LeftMiddle";

        	if ($('#' + source_id).attr('class') == 'seq_box') {
        		target_anc = "RightMiddle";
        	}

        	this.draw_arrow(source_id, target_id, "RightMiddle", target_anc, is_curved);
        }
	}

	this.add_ret_val = function(frame_id, value) {
		$("#" + frame_id).append("<tr><td>Returns</td><td id='" +
		frame_id + "0'>" + this.extract_value(value) + "</td></tr>");

		if (value.type == "function" || value.type == "seq") {
			this.draw_arrow(frame_id + "0", value.hash, "RightMiddle", "LeftMiddle", false);
		}
	}

	this.clear = function() {
		jsPlumb.detachEveryConnection();
		jsPlumb.deleteEveryEndpoint();

		$("#stack").empty();
		$("#heap").empty();
		$("#control").empty();
	}

	this.remove_component = function(id) {
		$("#" + id).remove();
	}

	this.draw_arrow = function(source_id, target_id, source_anc, target_anc, is_curved) {
		var connector_type = (is_curved) ? ["Bezier", {curviness:100}] : ["Straight"];

		var conn = jsPlumb.connect({
	        source: "" + source_id,
	        target: "" + target_id,
	        connector: connector_type,
	        paintStyle: { lineWidth:2.5, strokeStyle:'black' },
	        endpoint: ["Dot", {radius:1}],
	        overlays:[[ "Arrow", {location:1.0, width:10} ],],
	        anchors: [source_anc, target_anc],
	        hoverPaintStyle : {strokeStyle: "#0000ff"} 
    	});
	}
}
