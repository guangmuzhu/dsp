#!/bin/bash
#
# Prepend the Apache 2.0 license to the java source and change the
# Delphix copyright header to include the optional creation year as
# well as the year of the most recent change.
#
#  java-license <top level source directory>
#
# This script assumes the java source conforms with the standard Delphix
# copyright format which always appears at the beginning of a java source
# file and is separated by a single blank line from the file content:
#
# ^/\*\*$
# ^ \* Copyright \(c\) \d\d\d\d by Delphix\.$
# ^ \* All rights reserved.$
# ^ \*/$
#
# After the transformation is applied, the file header will look like the
# following.
#
# /**
#  * Licensed under the Apache License, Version 2.0 (the "License");
#  * you may not use this file except in compliance with the License.
#  * You may obtain a copy of the License at
#  *
#  *     http://www.apache.org/licenses/LICENSE-2.0
#  *
#  * Unless required by applicable law or agreed to in writing, software
#  * distributed under the License is distributed on an "AS IS" BASIS,
#  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  * See the License for the specific language governing permissions and
#  * limitations under the License.
#  */
#
# /**
#  * Copyright (c) 2013, 2014 by Delphix. All rights reserved.
#  */
#
# Run the script from the git repo where the source files reside.
#

SCRIPTDIR=`dirname $0`
LICENSELEN=`wc -l $SCRIPTDIR/java-delphix-license.txt | awk '{ print $1 }'`
SKIPLEN=`expr $LICENSELEN + 1`

TMPFILE='/tmp/license.$$'

for x in `find $1 -type f -name \*.java -print`; do
    RECENT_YEAR=`head -n $LICENSELEN $x | sed -e 's/[^0-9]*\([0-9]*\).*/\1/g' | tr -d '\n'`
    CREATE_YEAR=`git log --pretty=format:%cd $x | awk '{ print $5 }'  | tail -1`

    cat $SCRIPTDIR/java-apache-license.txt > $TMPFILE
    echo "" >> $TMPFILE
    echo "/**" >> $TMPFILE

    if [ $RECENT_YEAR -eq $CREATE_YEAR ]; then
        echo " * Copyright (c) $CREATE_YEAR by Delphix. All rights reserved." >> $TMPFILE
    else
        echo " * Copyright (c) $CREATE_YEAR, $RECENT_YEAR by Delphix. All rights reserved." >> $TMPFILE
    fi

    echo " */" >> $TMPFILE

    tail +$SKIPLEN $x >> $TMPFILE
    mv $TMPFILE $x
done
