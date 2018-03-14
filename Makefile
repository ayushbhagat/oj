.PHONY: build convert_dfa_regex_to_string create_scanner_dfa clean create_jlalr_dfa convert_jlalr_output_format_to_dfa run_marmoset_tests

default: build convert_dfa_regex_to_string create_scanner_dfa create_jlalr_dfa convert_jlalr_output_format_to_dfa

build:
	mkdir -p gen
	chmod +x joosc
	kotlinc src/main/* -include-runtime -d gen/oj.jar
	mkdir -p gen/jlalr
	javac jlalr/Jlalr1.java -d gen/jlalr

convert_dfa_regex_to_string:
	java -cp gen/oj.jar oj.scripts.ConvertDFARegexToStringKt

create_scanner_dfa:
	java -cp gen/oj.jar oj.scripts.CreateScannerDFAKt

create_jlalr_dfa:
	java -cp gen/oj.jar oj.scripts.ConvertCFGToJLALRInputFormatKt
	java -cp gen/jlalr jlalr.Jlr1 < gen/joos-jlalr-input-format.cfg > gen/joos-jlalr-lr1-output.dfa

convert_jlalr_output_format_to_dfa:
	java -cp gen/oj.jar oj.scripts.ConvertJLALROutputFormatToDFAKt

run_marmoset_tests:
	java -cp gen/oj.jar oj.scripts.RunMarmosetTestsKt

clean:
	rm -rf gen
