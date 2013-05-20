from http.server import BaseHTTPRequestHandler, HTTPServer
import copy
import json
import snappy

import util
import pprint
import inspect

ORIG_GLOB_NAMES = None

class SnappyHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        pass

    def do_POST(self):
        length = int(self.headers["Content-Length"])
        post_data = str(self.rfile.read(length), "utf-8")[1 : -1].replace("\\n", "\n").replace("\\t", "\t").replace('\\"', '"')

        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "http://localhost:8000")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Content-Type", "application/json")
        self.end_headers()

        self.wfile.write(json.dumps(handle_post_request(post_data)).encode("utf-8"))

    def do_OPTIONS(self):
        origin = self.headers["Origin"]
        if origin == "http://localhost:8000":
            self.send_response(200)
            self.send_header("Access-Control-Allow-Origin", origin)
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.end_headers()


def handle_post_request(post_data):
    global ORIG_GLOB_NAMES
    if ORIG_GLOB_NAMES == None:
        ORIG_GLOB_NAMES = set(globals().keys())

    snap = snappy.Snappy()
    snap.preprocess(post_data)
    snap.run(post_data)

    cur_globals = globals()
    for k in set(cur_globals.keys()):
        if k not in ORIG_GLOB_NAMES:
            del cur_globals[k]

    return {"snapshots" : util.snapshots_to_json(snap.snapshots), "pointers" : util.parent_pointers_to_json(snap.func_to_parent), 
    "if_nodes" : snap.preprocessor.if_nodes, "while_nodes" : snap.preprocessor.while_nodes}



if __name__ == "__main__":
    try:
        server = HTTPServer(("", 8880), SnappyHandler)
        print("Started SnappyServer...")
        server.serve_forever()
    except KeyboardInterrupt:
        print("Keyboard interrupt received, shutting down Snappy Server...")
        server.socket.close()
        