Implementation of a simple banking service.
The service consists of three independent programs:

	- exchange rates provider (written in java): simulates exchange rates fluctuations;
	- bank (written in python): provides basic account functionality with support for multiple currencies;
	- client (written in python): provides access to bank's API through command line interface.

Example setup:
	- compile idl files: run 'make idl' in both python and java/grpc-currency-server subdirectories
	- run exchange rates provider with 'make run-rates-provider' from java/grpc-currency-server
	- run bank with 'python3 bank.py config.json bank1' from python/src/app
	- run client with 'python3 client.py 127.0.0.1 9991' from python/src/app

(note: you need to run all commands from proper directories due to relative import paths) 
