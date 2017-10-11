/*
 * Copyright 2017 Nicolas Morel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.nmorel.gwtjackson.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.nmorel.gwtjackson.client.AbstractObjectReader;
import com.github.nmorel.gwtjackson.client.JsonDeserializer;
import com.github.nmorel.gwtjackson.client.annotation.JsonMapper;
import com.github.nmorel.gwtjackson.client.annotation.JsonReader;
import com.github.nmorel.gwtjackson.client.annotation.JsonWriter;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractBeanJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.HasDeserializerAndParameters;
import com.github.nmorel.gwtjackson.client.deser.bean.InstanceBuilder;
import com.github.nmorel.gwtjackson.client.deser.bean.SimpleStringMap;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import static java.util.Objects.isNull;

@AutoService( Processor.class )
public class ObjectMapperProcessor extends AbstractProcessor {

    protected Types typeUtils;

    protected Elements elementUtils;

    protected Filer filer;

    protected Messager messager;

    @Override
    public synchronized void init( ProcessingEnvironment processingEnv ) {
        super.init( processingEnv );
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process( Set<? extends TypeElement> annotations, RoundEnvironment roundEnv ) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith( JsonReader.class );
        elements.forEach( this::generateReader );
        return false;
    }

    private void generateReader( Element element ) {
        try {
            String className = enclosingName( element, "_" ) + element.getSimpleName() + "Impl";
            String packageName = elementUtils.getPackageOf( element ).getQualifiedName().toString();
            Name beanName = typeUtils.asElement( getBeanType( element ) ).getSimpleName();

            makeBeanDeserializer( element, beanName, packageName );

            MethodSpec constructor = makeConstructor( beanName );
            MethodSpec newDeserializer = makeNewDeserializerMethod( element, beanName );

            TypeSpec classSpec = TypeSpec.classBuilder( className )
                    .addModifiers( Modifier.PUBLIC, Modifier.FINAL )
                    .superclass( abstractObjectReader( element ) )
                    .addSuperinterface( TypeName.get( element.asType() ) )
                    .addMethod( constructor )
                    .addMethod( newDeserializer )
                    .build();

            JavaFile.builder( packageName, classSpec ).build().writeTo( filer );
        } catch ( IOException e ) {
            messager.printMessage( Kind.ERROR, "error while creating source file " + e, element );
        }
    }

    private void makeBeanDeserializer( Element element, Name beanName, String packageName ) throws IOException {

        MethodSpec constructor = MethodSpec.constructorBuilder().addModifiers( Modifier.PUBLIC ).build();

        MethodSpec getDeserializedType = MethodSpec.methodBuilder( "getDeserializedType" )
                .addModifiers( Modifier.PUBLIC )
                .addAnnotation( Override.class )
                .returns( ClassName.get( Class.class ) )
                .addStatement( "return " + beanName + ".class" )
                .build();
        ParameterizedTypeName parameterizedTypeName = ParameterizedTypeName.get( ClassName.get( SimpleStringMap.class ), ClassName
                .get( HasDeserializerAndParameters.class ) );

        MethodSpec initInstanceBuilder = MethodSpec.methodBuilder( "initInstanceBuilder" )
                .addModifiers( Modifier.PROTECTED )
                .returns( ParameterizedTypeName.get( ClassName.get( InstanceBuilder.class ),
                        ClassName.get( getBeanType( element ) ) ) )
                .addAnnotation( Override.class )
                .addStatement( "final $T deserializers = null" , parameterizedTypeName)
                .build();

        TypeSpec deserializerImpl = TypeSpec.classBuilder( beanName + "BeanJsonDeserializerImpl" )
                .addModifiers( Modifier.PUBLIC, Modifier.FINAL )
                .superclass( ParameterizedTypeName.get( ClassName.get( AbstractBeanJsonDeserializer.class ),
                        ClassName.get( getBeanType( element ) ) ) )
                .addMethod( constructor )
                .addMethod( getDeserializedType )
                .addMethod( initInstanceBuilder )
                .build();

        JavaFile.builder( packageName, deserializerImpl ).build().writeTo( filer );
    }

    private MethodSpec makeNewDeserializerMethod( Element element, Name beanName ) {
        return MethodSpec.methodBuilder( "newDeserializer" )
                .addModifiers( Modifier.PROTECTED )
                .addAnnotation( Override.class )
                .returns( ParameterizedTypeName.get( ClassName.get( JsonDeserializer.class ),
                        ClassName.get( getBeanType( element ) ) ) )
                .addStatement( "return new " + beanName + "BeanJsonDeserializerImpl()" )
                .build();
    }

    private MethodSpec makeConstructor( Name beanName ) {
        return MethodSpec.constructorBuilder()
                .addModifiers( Modifier.PUBLIC )
                .addStatement( "super(\"" + beanName + "\")" ).build();
    }

    private TypeName abstractObjectReader( Element element ) {
        TypeMirror beanType = getBeanType( element );
        return ParameterizedTypeName.get( ClassName.get( AbstractObjectReader.class ),
                ClassName.get( beanType ) );
    }

    private TypeMirror getBeanType( Element element ) {
        TypeMirror objectReader = ((TypeElement) typeUtils.asElement( element.asType() )).getInterfaces().get( 0 );
        return MoreTypes.asDeclared( objectReader ).getTypeArguments().get( 0 );
    }

    private String enclosingName( Element element, String postfix ) {
        if ( isNull( element.getEnclosingElement() ) ) {
            return "";
        }
        return element.getEnclosingElement().getSimpleName().toString() + postfix;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Stream
                .of( JsonReader.class, JsonWriter.class, JsonMapper.class )
                .map( Class::getCanonicalName ).collect( Collectors.toSet() );
    }
}
