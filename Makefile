.PHONY: build convert_dfa_regex_to_string create_scanner_dfa clean

default:
	make build
	make convert_dfa_regex_to_string
	make create_scanner_dfa

build:
	mkdir -p gen
	chmod +x joosc
	kotlinc src/* -d gen/oj.jar

convert_dfa_regex_to_string:
	kotlin -cp gen/oj.jar scripts.ConvertDFARegexToStringKt

create_scanner_dfa:
	kotlin -cp gen/oj.jar scripts.CreateScannerDFAKt

clean:
	rm -rf gen
