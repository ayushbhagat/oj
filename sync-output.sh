#!/bin/bash
SYNC="rsync -avz --delete ./output/ rsnara@linux.student.cs.uwaterloo.ca:cs444/a5/output && rsync -av ./test/marmoset/stdlib/5.0/runtime.s rsnara@linux.student.cs.uwaterloo.ca:cs444/a5/output/runtime.s"
rsync -av ./run.sh rsnara@linux.student.cs.uwaterloo.ca:cs444/a5/run.sh

eval ${SYNC}

watchman-make -p 'output/**/*' -r "${SYNC}"
