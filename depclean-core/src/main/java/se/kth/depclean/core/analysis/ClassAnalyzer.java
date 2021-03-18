package se.kth.depclean.core.analysis;

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

import java.io.IOException;
import java.net.URL;
import java.util.Set;

/**
 * Gets the set of classes contained in an artifact given either
 * as a jar file or an exploded directory.
 */
public interface ClassAnalyzer {

  // fields -----------------------------------------------------------------

  /**
   * To store the name of the class.
   */
  String ROLE = ClassAnalyzer.class.getName();

  // public methods ---------------------------------------------------------

  /**
   * Analyze the classes of a given artifact.
   *
   * @param url The artifact.
   * @return A set of classes.
   * @throws IOException In case of IO issues.
   */
  Set<String> analyze(URL url)
      throws IOException;
}
