gen_proto: ./exchange_rate.proto 
	python3.6 -m grpc_tools.protoc -I=. --python_out=../proto_out/ --grpc_python_out=../proto_out/ ./exchange_rate.proto

start-server:
	python3.6 ./currencyServer.py

start-client:
	python3.6 ./testclient.py
