idl:
	python3.6 -m grpc_tools.protoc -I=./idl-def/grpc --python_out=./src/idl/grpc --grpc_python_out=./src/idl/grpc ./idl-def//grpc/exchange_rate.proto
	slice2py ./idl-def/ice/Accounts.ice --output-dir ./src/idl/ice

clean:
	rm -rf ./src/idl/ice/* ./src/idl/grpc/*
