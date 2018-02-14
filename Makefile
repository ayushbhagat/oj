.PHONY: build convert_dfa_regex_to_string create_scanner_dfa clean create_jlalr_dfa convert_jlalr_output_format_to_dfa

default: build convert_dfa_regex_to_string create_scanner_dfa create_jlalr_dfa convert_jlalr_output_format_to_dfa

build:
	mkdir -p gen
	chmod +x joosc
	kotlinc src/main/* -include-runtime -d gen/oj.jar
	javac jlalr/Jlalr1.java -d gen/jlalr.jar

convert_dfa_regex_to_string:
	java -cp gen/oj.jar oj.scripts.ConvertDFARegexToStringKt

create_scanner_dfa:
	java -cp gen/oj.jar oj.scripts.CreateScannerDFAKt

create_jlalr_dfa:
	java -cp gen/oj.jar oj.scripts.ConvertCFGToJLALRInputFormatKt
	java -cp gen/jlalr.jar jlalr.Jlr1 < gen/joos-jlalr-input-format.cfg > gen/joos-jlalr-lr1-output.dfa

convert_jlalr_output_format_to_dfa:
	java -cp gen/oj.jar oj.scripts.ConvertJLALROutputFormatToDFAKt

clean:
	rm -rf gen
