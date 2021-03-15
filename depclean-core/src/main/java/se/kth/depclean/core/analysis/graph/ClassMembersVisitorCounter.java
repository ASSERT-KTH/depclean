/*
 * Copyright (c) 2020, CASTOR Software Research Centre (www.castor.kth.se)
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package se.kth.depclean.core.analysis.graph;

/**
 * A counter to keep track of the class members in the dependencies.
 */
public final class ClassMembersVisitorCounter {

  /**
   * Number of types.
   */
  private static long nbVisitedTypes;

  /**
   * Number of fields.
   */
  private static long nbVisitedFields;

  /**
   * Number of methods.
   */
  private static long nbVisitedMethods;

  /**
   * Number of annotations.
   */
  private static long nbVisitedAnnotations;

  private ClassMembersVisitorCounter() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Sets all the class counters to zero.
   */
  public static void resetClassCounters() {
    nbVisitedTypes = 0;
    nbVisitedFields = 0;
    nbVisitedMethods = 0;
    nbVisitedAnnotations = 0;
  }

  public static void addVisitedClass() {
    nbVisitedTypes++;
  }

  public static void addVisitedField() {
    nbVisitedFields++;
  }

  public static void addVisitedMethod() {
    nbVisitedMethods++;
  }

  public static void addVisitedAnnotation() {
    nbVisitedAnnotations++;
  }

  public static long getNbVisitedTypes() {
    return nbVisitedTypes;
  }

  public static long getNbVisitedFields() {
    return nbVisitedFields;
  }

  public static long getNbVisitedMethods() {
    return nbVisitedMethods;
  }

  public static long getNbVisitedAnnotations() {
    return nbVisitedAnnotations;
  }
}
