#!/bin/bash
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# Copyright (c) 2012 by Delphix. All rights reserved.
#

#
# This script is used as part of the eclipse-format-check ant task. It diffs a
# formatted directory of Java files with the original. If any files differ they
# are output with a user friendly error message and the script exits with a
# non-zero exit code.
#

if [[ $# -ne 2 ]]; then
	echo "Usage: $0 formatted_dir original_dir" >&2
        exit 2
fi

formatted=$1
original=$2

result=0

cd "$formatted"
for file in $(find . -name "*.java"); do
	if ! diff "$file" "$original/$file" >/dev/null 2>&1; then
		echo $(readlink -f "$original/$file") >&2
	result=1
	fi
done

if [[ $result -ne 0 ]]; then
	echo "Run the Eclipse auto-formatter on the listed files." >&2
	echo "See https://sites.google.com/a/delphix.com/engineering/java-coding-standards#autoFormatting" >&2
	echo "" >&2
fi

exit $result
