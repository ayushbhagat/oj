.PHONY: build convert_dfa_regex_to_string create_scanner_dfa clean

default: build convert_dfa_regex_to_string create_scanner_dfa

build:
	mkdir -p gen
	chmod +x joosc
	kotlinc src/main/* -include-runtime -d gen/oj.jar

convert_dfa_regex_to_string:
	java -cp gen/oj.jar oj.scripts.ConvertDFARegexToStringKt

create_scanner_dfa:
	java -cp gen/oj.jar oj.scripts.CreateScannerDFAKt

clean:
	rm -rf gen
