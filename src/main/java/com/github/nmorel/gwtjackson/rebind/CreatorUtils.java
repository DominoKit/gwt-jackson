package com.github.nmorel.gwtjackson.rebind;

import java.lang.annotation.Annotation;

import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JType;

/** @author Nicolas Morel */
public final class CreatorUtils
{
    /**
     * Browse all the hierarchy of the type and return the first corresponding annotation it found
     *
     * @param type type
     * @param annotation annotation to find
     * @param <T> type of the annotation
     * @return the annotation if found, null otherwise
     */
    public static <T extends Annotation> T findFirstEncounteredAnnotationsOnAllHierarchy( JClassType type, Class<T> annotation )
    {
        JClassType currentType = type;
        while ( null != currentType && !currentType.getQualifiedSourceName().equals( "java.lang.Object" ) )
        {
            if ( currentType.isAnnotationPresent( annotation ) )
            {
                return currentType.getAnnotation( annotation );
            }
            for ( JClassType interf : currentType.getImplementedInterfaces() )
            {
                T annot = findFirstEncounteredAnnotationsOnAllHierarchy( interf, annotation );
                if ( null != annot )
                {
                    return annot;
                }
            }
            currentType = currentType.getSuperclass();
        }
        return null;
    }

    public static <T extends Annotation> T findAnnotationOnAnyAccessor( FieldAccessors fieldAccessors, Class<T> annotation )
    {
        // TODO with this current setup, an annotation present on a getter method in superclass will be returned instead of the same
        // annotation present on field in the child class. Test the behaviour in jackson.

        if ( null != fieldAccessors.getGetter() && fieldAccessors.getGetter().isAnnotationPresent( annotation ) )
        {
            return fieldAccessors.getGetter().getAnnotation( annotation );
        }
        if ( null != fieldAccessors.getSetter() && fieldAccessors.getSetter().isAnnotationPresent( annotation ) )
        {
            return fieldAccessors.getSetter().getAnnotation( annotation );
        }
        if ( null != fieldAccessors.getField() && fieldAccessors.getField().isAnnotationPresent( annotation ) )
        {
            return fieldAccessors.getField().getAnnotation( annotation );
        }

        for ( JMethod method : fieldAccessors.getGetters() )
        {
            if ( method.isAnnotationPresent( annotation ) )
            {
                return method.getAnnotation( annotation );
            }
        }

        for ( JMethod method : fieldAccessors.getSetters() )
        {
            if ( method.isAnnotationPresent( annotation ) )
            {
                return method.getAnnotation( annotation );
            }
        }

        return null;
    }

    private CreatorUtils()
    {
    }

    /**
     * Extract the bean type from the type given in parameter. For {@link java.util.Collection}, it gives the bounded type. For {@link
     * java.util.Map}, it gives the second bounded type. Otherwise, it gives the type given in parameter.
     *
     * @param type type to extract the bean type
     * @return
     */
    public static JClassType extractBeanType( JacksonTypeOracle typeOracle, JType type )
    {
        JClassType classType = type.isClassOrInterface();
        if ( null == classType )
        {
            return null;
        }
        else if ( typeOracle.isIterable( classType ) )
        {
            return extractBeanType( typeOracle, classType.isParameterized().getTypeArgs()[0] );
        }
        else if ( typeOracle.isMap( classType ) )
        {
            return extractBeanType( typeOracle, classType.isParameterized().getTypeArgs()[1] );
        }
        else
        {
            return classType;
        }
    }
}