#!/bin/ksh
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
# Copyright (c) 2013 by Delphix. All rights reserved.
#

PATH=/opt/sunstudio12.1/bin:/usr/xpg4/bin:/usr/bin:$PATH

#
# This script is used to build the platform specific components of the
# Delphix appliance. It sets the OS and MACH parameters which are consumed
# by the various makefiles.
#

fail()
{
	echo $*
	exit 1
}

OS=$(uname | tr -d '[:punct:]' | tr '[:upper:]' '[:lower:]')
export OS

case ${OS} in
sunos)
	MACH=$(uname -p)
	;;
hpux)
	MACH=$(uname -m)
	;;
aix)
	MACH=$(uname -p)
	;;
linux)
	MACH=$(uname -p)
	;;
*)
	fail "Invalid os type: ${OS}"
	;;
esac

#
# We map all the intel/amd processor variants to x86. This must match the
# mapping used by DHM (appliance/server/dhm/src/xml/os.xml)
#
case ${MACH} in
x86_64|i386|i486|i586|i686-64|i686)
	MACH=x86
	;;
esac
export MACH

make all
