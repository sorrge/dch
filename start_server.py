import http.server
import ssl

class HTTPServer(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        super().do_GET()

def run(server_class=http.server.HTTPServer, handler_class=HTTPServer):
    server_address = ('0.0.0.0', 4443)  # Port 4443 for HTTPS
    httpd = server_class(server_address, handler_class)
    httpd.socket = ssl.wrap_socket(httpd.socket, keyfile="certificates/key.pem", certfile="certificates/cert.pem", server_side=True)
    print('HTTPS server running on https://localhost:4443')
    httpd.serve_forever()

if __name__ == "__main__":
    run()
