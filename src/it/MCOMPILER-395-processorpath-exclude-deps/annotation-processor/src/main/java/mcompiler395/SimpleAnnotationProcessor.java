/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package mcompiler395;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("mcompiler395.SimpleAnnotation")
public class SimpleAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return true;
        }

        // assert that mcompiler395-annotation-processor-dep is NOT on the processorpath, since it is excluded
        // in the plugin configuration
        try {
            getClass().getClassLoader().loadClass("mcompiler395.AnnotationProcessorDependency");
            throw new RuntimeException("Expected a ClassNotFoundException, because "
                    + "mcompiler395.AnnotationProcessorDependency is not supposed to be on the processorpath.");
        } catch (ClassNotFoundException expected) {
            // expected
        }

        Filer filer = processingEnv.getFiler();
        Elements elementUtils = processingEnv.getElementUtils();
        Set<? extends Element> elements =
                roundEnv.getElementsAnnotatedWith(annotations.iterator().next());

        for (Element element : elements) {
            Name name = element.getSimpleName();
            PackageElement packageElement = elementUtils.getPackageOf(element);
            try {
                Name packageName = packageElement.getQualifiedName();
                FileObject resource =
                        filer.createResource(StandardLocation.SOURCE_OUTPUT, packageName, name + ".txt", element);

                Writer writer = resource.openWriter();
                writer.write(name.toString());
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return !elements.isEmpty();
    }
}
