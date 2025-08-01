/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package se.kth.depclean.core.analysis.asm;

import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;

/** Used for storing the types visited statically. */
public class ResultCollector {

  private final Set<String> classes = new HashSet<>();

  @NonNull
  public Set<String> getDependencies() {
    return classes;
  }

  public void clearClasses() {
    classes.clear();
  }

  public void addDesc(@NonNull final String desc) {
    addType(Type.getType(desc));
  }

  void addType(@NonNull final Type t) {
    switch (t.getSort()) {
      case Type.ARRAY -> addType(t.getElementType());
      case Type.OBJECT -> addName(t.getClassName().replace('.', '/'));
      default -> {
        // no-op
      }
    }
  }

  public void add(@NonNull String name) {
    classes.add(name);
  }

  void addNames(@Nullable final String[] names) {
    if (names == null) {
      return;
    }
    for (String name : names) {
      addName(name);
    }
  }

  void addName(@Nullable String name) {
    if (name == null) {
      return;
    }
    // decode arrays
    if (name.startsWith("[L") && name.endsWith(";")) {
      name = name.substring(2, name.length() - 1);
    }
    // decode internal representation
    name = name.replace('/', '.');
    classes.add(name);
  }

  void addMethodDesc(@NonNull final String desc) {
    addType(Type.getReturnType(desc));
    Type[] types = Type.getArgumentTypes(desc);
    for (Type type : types) {
      addType(type);
    }
  }
}
