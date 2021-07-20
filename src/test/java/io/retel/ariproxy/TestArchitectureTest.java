package io.retel.ariproxy;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import akka.actor.ActorPath;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeJars;
import com.tngtech.archunit.core.importer.ImportOption.OnlyIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.retel.ariproxy",
    importOptions = {OnlyIncludeTests.class, DoNotIncludeJars.class})
public class TestArchitectureTest {

  @ArchTest
  public static final ArchRule NOTHING_DEPENDS_ON_AKKA_CLASSIC =
      classes()
          .should()
          .onlyDependOnClassesThat(
              resideOutsideOfPackage("akka.actor..")
                  .or(resideInAPackage("akka.actor.typed.."))
                  .or(resideInAPackage("akka.actor.testkit.typed.."))
                  .or(type(ActorPath.class)));
}
