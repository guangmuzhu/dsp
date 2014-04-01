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

#
# Common flags for all platforms.
#
TOPDIR=.
SRCDIR=$(TOPDIR)/src
BLDDIR=$(TOPDIR)/build

OBJDIR=$(BLDDIR)/obj
OBJDIR64=$(BLDDIR)/obj64
JNIDIR=$(BLDDIR)/jni-headers

TOPINC= -I$(SRCDIR)/lz4 -I$(SRCDIR)/xxhash -I$(JNIDIR)

OBJ=$(OBJDIR)/lz4.o $(OBJDIR)/lz4hc.o $(OBJDIR)/xxhash.o \
	$(OBJDIR)/net_jpountz_lz4_LZ4JNI.o $(OBJDIR)/net_jpountz_xxhash_XXHashJNI.o
OBJ64=$(OBJDIR64)/lz4.o $(OBJDIR64)/lz4hc.o $(OBJDIR64)/xxhash.o \
	$(OBJDIR64)/net_jpountz_lz4_LZ4JNI.o $(OBJDIR64)/net_jpountz_xxhash_XXHashJNI.o

LDLIBS=
LDLIBS64=

#
# Platform makefiles are used to define OS specific
# flags, compilers, and make targets (i.e. relink target).
#
include $(OS)/Makefile.$(OS)

LIBDIR=$(BLDDIR)/jni/$(OS)/$(MACH)/lib
LIBDIR64=$(BLDDIR)/jni/$(OS)/$(MACH)/lib64

all: lz4 lz4-64

$(OBJDIR) $(OBJDIR64) $(LIBDIR) $(LIBDIR64) $(JNIDIR):
	mkdir -p $@

jni-headers: $(JNIDIR)
	$(JAVA_HOME)/bin/javah -d $(BLDDIR)/jni-headers -classpath $(TOPDIR)/classes \
	net.jpountz.lz4.LZ4JNI net.jpountz.xxhash.XXHashJNI

$(OBJDIR)/lz4.o: $(SRCDIR)/lz4/lz4.c
	$(COMPILE.c) -o $@ $(SRCDIR)/lz4/lz4.c

$(OBJDIR)/lz4hc.o: $(SRCDIR)/lz4/lz4hc.c
	$(COMPILE.c) -o $@ $(SRCDIR)/lz4/lz4hc.c

$(OBJDIR)/net_jpountz_lz4_LZ4JNI.o: $(SRCDIR)/jni/net_jpountz_lz4_LZ4JNI.c
	$(COMPILE.c) -o $@ $(SRCDIR)/jni/net_jpountz_lz4_LZ4JNI.c

$(OBJDIR)/xxhash.o: $(SRCDIR)/xxhash/xxhash.c
	$(COMPILE.c) -o $@ $(SRCDIR)/xxhash/xxhash.c

$(OBJDIR)/net_jpountz_xxhash_XXHashJNI.o: $(SRCDIR)/jni/net_jpountz_xxhash_XXHashJNI.c
	$(COMPILE.c) -o $@ $(SRCDIR)/jni/net_jpountz_xxhash_XXHashJNI.c

lz4: $(OBJDIR) $(LIBDIR) jni-headers $(OBJ)
	$(CC) $(CFLAGS) $(LFLAGS) -o $(LIBDIR)/liblz4-java.so $(OBJ) $(LDLIBS)

$(OBJDIR64)/lz4.o: $(SRCDIR)/lz4/lz4.c
	$(COMPILE64.c) -o $@ $(SRCDIR)/lz4/lz4.c

$(OBJDIR64)/lz4hc.o: $(SRCDIR)/lz4/lz4hc.c
	$(COMPILE64.c) -o $@ $(SRCDIR)/lz4/lz4hc.c

$(OBJDIR64)/net_jpountz_lz4_LZ4JNI.o: $(SRCDIR)/jni/net_jpountz_lz4_LZ4JNI.c
	$(COMPILE64.c) -o $@ $(SRCDIR)/jni/net_jpountz_lz4_LZ4JNI.c

$(OBJDIR64)/xxhash.o: $(SRCDIR)/xxhash/xxhash.c
	$(COMPILE64.c) -o $@ $(SRCDIR)/xxhash/xxhash.c

$(OBJDIR64)/net_jpountz_xxhash_XXHashJNI.o: $(SRCDIR)/jni/net_jpountz_xxhash_XXHashJNI.c
	$(COMPILE64.c) -o $@ $(SRCDIR)/jni/net_jpountz_xxhash_XXHashJNI.c

lz4-64: $(OBJDIR64) $(LIBDIR64) jni-headers $(OBJ64)
	$(CC) $(CFLAGS64) $(LFLAGS64) -o $(LIBDIR64)/liblz4-java.so $(OBJ64) $(LDLIBS64)
