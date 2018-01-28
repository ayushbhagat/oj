.PHONY: build convert_dfa_regex_to_char_code create_scanner_dfa clean

default:
	make build
	make convert_dfa_regex_to_char_code
	make create_scanner_dfa

build:
	kotlinc src/* -d gen/oj.jar

convert_dfa_regex_to_char_code:
	kotlin -cp gen/oj.jar scripts.ConvertDFARegexToCharCodeKt

create_scanner_dfa:
	kotlin -cp gen/oj.jar scripts.CreateScannerDFAKt

clean:
	rm -rf gen/*
