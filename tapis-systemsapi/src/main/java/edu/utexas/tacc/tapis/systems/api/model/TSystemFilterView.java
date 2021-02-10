package edu.utexas.tacc.tapis.systems.api.model;

import org.glassfish.jersey.internal.inject.AnnotationLiteral;
import org.glassfish.jersey.message.filtering.EntityFiltering;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Entity-filtering annotation for api model TSystem.
 * Used to support Jersey's entity filtering mechanism.
 * Possibly this support could be made generic (to include Credential, perhaps), but it
 * seems like that would not be a good balance between re-usability and readability.
 * See Jersey 2.33 doc Chapter 20 Entity Data Filtering,
 *   especially section 6 - Defining custom handling for entity-filtering annotations
 *
 */
//@Target({ElementType.TYPE})
//@Retention(RetentionPolicy.RUNTIME)
//@EntityFiltering
public @interface TSystemFilterView
{
//  // Entity-filtering scope
//  Annotation filteringScope();
//
//  // Fields to include
//  String[] fields();
//
//  /**
//   * Factory class for creating instances of TSystemFilterView annotation.
//   */
//  public static class Factory extends AnnotationLiteral<TSystemFilterView> implements TSystemFilterView
//  {
//    private Factory() { }
//    public static TSystemFilterView get() {
//      return new Factory();
//    }
//  }
}
