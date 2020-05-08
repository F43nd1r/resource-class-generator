package com.faendir.maven;

/*
 * Copyright 2019 Lukas Morawietz (https://github.com/F43nd1r)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.base.CaseFormat;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.squareup.kotlinpoet.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mojo which generates a class with constants from message resource bundles
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class MessageGenerator extends AbstractMojo {
    @Parameter(property = "inputDirectory", defaultValue = "src/main/resources")
    private File inputDirectory;
    @Parameter(property = "outputDirectory", defaultValue = "target/generated-sources/java")
    private File outputDirectory;
    @Parameter(property = "packageName", defaultValue = "com.faendir.i18n")
    private String packageName;
    @Parameter(property = "className", defaultValue = "Messages")
    private String className;
    @Parameter(property = "language", defaultValue = "java")
    private String language;

    public void execute() throws MojoExecutionException {
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        Set<String> keys = new HashSet<>();
        try {
            Files.walk(inputDirectory.toPath())
                    .filter(file -> file.toString().endsWith(".properties"))
                    .peek(file -> getLog().info("Generating messages for " + file))
                    .forEach(file -> {
                        try {
                            Properties properties = new Properties();
                            properties.load(new FileReader(file.toFile()));
                            keys.addAll(Collections.list(properties.keys()).stream().map(Object::toString).collect(Collectors.toList()));
                        } catch (Exception ignored) {
                        }
                    });
            switch (language) {
                case "java":
                    writeJava(keys);
                    break;
                case "kotlin":
                    writeKotlin(keys);
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeKotlin(Set<String> keys) throws IOException {
        com.squareup.kotlinpoet.TypeSpec.Builder builder = com.squareup.kotlinpoet.TypeSpec.objectBuilder(className);
        for(String key: keys) {
            builder.addProperty(PropertySpec.builder(CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, key), TypeNames.STRING, KModifier.CONST).initializer("%S", key).build());
        }
        FileSpec.builder(packageName, className)
                .addType(builder.build())
                .build()
                .writeTo(outputDirectory);
    }

    private void writeJava(Set<String> keys) throws IOException {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        for (String key : keys) {
            classBuilder.addField(FieldSpec.builder(String.class, CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, key), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$S", key)
                    .build());
        }
        JavaFile.builder(packageName, classBuilder.build())
                .skipJavaLangImports(true)
                .indent("    ")
                .build()
                .writeTo(outputDirectory);
    }
}
