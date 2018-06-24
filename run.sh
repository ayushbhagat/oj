#!/usr/bin/env bash

find ./output -iname '*.s' | xargs -I {} nasm -O1 -f elf -g -F dwarf {}
ld -melf_i386 -o main output/*.o

./main
echo "Return:" $?