package se.kth.jdbl;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.ReferenceTypeFilter;

import java.util.HashSet;
import java.util.Set;

public class CtTypeProcessor extends AbstractProcessor<CtType> {

    @Override
    public void process(CtType ctType) {
        getTypeReferences(ctType);
    }

    private Set<CtTypeReference<?>> getTypeReferences(CtType type) {
        Set<CtTypeReference<?>> typeReferences = new HashSet<>();
        for (CtTypeReference<?> typeReference : Query.getReferences(type, new ReferenceTypeFilter<>(CtTypeReference.class))) {
            if (!(typeReference.isPrimitive()
                    || (typeReference instanceof CtArrayTypeReference)
                    || typeReference.toString().equals(CtTypeReference.NULL_TYPE_NAME)
                    || (typeReference.getPackage() != null && "java.lang".equals(typeReference.getPackage().toString())))
                    && !(typeReference.getQualifiedName().startsWith("java."))
                    && !(typeReference.getQualifiedName().startsWith("javax."))
                    && !(typeReference.getQualifiedName().startsWith("sun."))
                    && !(typeReference.getQualifiedName().startsWith("jdk."))
            ) {
                typeReferences.add(typeReference);
                TypeReferences.addType(typeReference.getQualifiedName());
            }
        }
        return typeReferences;
    }

    static class TypeReferences {
        static HashSet<String> types = new HashSet<>();

        public static void addType(String ctTypeReference) {
            types.add(ctTypeReference);
        }
    }

}
