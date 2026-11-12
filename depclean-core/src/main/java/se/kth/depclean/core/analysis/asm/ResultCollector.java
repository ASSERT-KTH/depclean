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

/** Collects the fully qualified names of the classes referenced by a class file. */
public class ResultCollector {

  private final Set<String> classes = new HashSet<>();

  /**
   * The classes collected so far.
   *
   * @return the fully qualified, dot-separated names of the referenced classes
   */
  @NonNull
  public Set<String> getDependencies() {
    return classes;
  }

  /**
   * Adds every class type that occurs in a field or method descriptor, e.g. {@code
   * (Lcom/foo/A;[Lcom/foo/B;I)Lcom/foo/C;}.
   *
   * @param desc a field or method descriptor
   */
  public void addDesc(@NonNull final String desc) {
    int i = 0;
    final int length = desc.length();
    while (i < length) {
      // Only class types start with 'L'; once inside a class name, skip to its terminating ';'.
      if (desc.charAt(i) == 'L') {
        int end = desc.indexOf(';', i);
        if (end < 0) {
          return;
        }
        classes.add(desc.substring(i + 1, end).replace('/', '.'));
        i = end + 1;
      } else {
        i++;
      }
    }
  }

  void addType(@NonNull final Type t) {
    switch (t.getSort()) {
      case Type.ARRAY:
        addType(t.getElementType());
        break;
      case Type.OBJECT:
        classes.add(t.getClassName());
        break;
      case Type.METHOD:
        addDesc(t.getDescriptor());
        break;
      default:
        // primitive types are not classes
        break;
    }
  }

  void addNames(@Nullable final String[] names) {
    if (names == null) {
      return;
    }
    for (String name : names) {
      addName(name);
    }
  }

  /**
   * Adds a class given by its internal name ({@code com/foo/Bar}) or, as the constant pool does for
   * array classes, by an array descriptor ({@code [Lcom/foo/Bar;}).
   *
   * @param name the internal name or array descriptor, ignored when null
   */
  void addName(@Nullable String name) {
    if (name == null || name.isEmpty()) {
      return;
    }
    if (name.charAt(0) == '[') {
      addDesc(name);
    } else {
      classes.add(name.replace('/', '.'));
    }
  }

  void addMethodDesc(@NonNull final String desc) {
    addDesc(desc);
  }
}
