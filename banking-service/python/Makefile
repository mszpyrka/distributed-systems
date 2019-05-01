idl: ./idl-src/exchange_rate.proto
	python3.6 -m grpc_tools.protoc -I=./idl-src --python_out=./idl_out/grpc --grpc_python_out=./idl_out/grpc ./idl-src/exchange_rate.proto
	slice2py ./idl-src/Accounts.ice --output-dir ./idl_out/ice
