package tools.jackson.jaxrs.smile;

import java.lang.annotation.Annotation;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;

import tools.jackson.core.*;

import tools.jackson.databind.*;

import tools.jackson.dataformat.smile.databind.SmileMapper;
import tools.jackson.jaxrs.base.ProviderBase;
import tools.jackson.jaxrs.smile.PackageVersion;

/**
 * Basic implementation of JAX-RS abstractions ({@link MessageBodyReader},
 * {@link MessageBodyWriter}) needed for binding
 * Smile ("application/x-jackson-smile") content to and from Java Objects ("POJO"s).
 *<p>
 * Actual data binding functionality is implemented by {@link ObjectMapper}:
 * mapper to use can be configured in multiple ways:
 * <ul>
 *  <li>By explicitly passing mapper to use in constructor
 *  <li>By explictly setting mapper to use by {@link #setMapper}
 *  <li>By defining JAX-RS <code>Provider</code> that returns {@link ObjectMapper}s.
 *  <li>By doing none of above, in which case a default mapper instance is
 *     constructed (and configured if configuration methods are called)
 * </ul>
 * The last method ("do nothing specific") is often good enough; explicit passing
 * of Mapper is simple and explicit; and Provider-based method may make sense
 * with Dependency Injection frameworks, or if Mapper has to be configured differently
 * for different media types.
 *<p>
 * Note that the default mapper instance will be automatically created if
 * one of explicit configuration methods (like {@link #configure})
 * is called: if so, Provider-based introspection is <b>NOT</b> used, but the
 * resulting Mapper is used as configured.
 *<p>
 * Note that there is also a sub-class -- ({@link JacksonJaxbSmileProvider}) -- that
 * is configured by default to use both Jackson and JAXB annotations for configuration
 * (base class when used as-is defaults to using just Jackson annotations)
 *
 * @author Tatu Saloranta
 */
@Provider
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.WILDCARD)
public class JacksonSmileProvider
extends ProviderBase<JacksonSmileProvider,
    SmileMapper,
    SmileEndpointConfig,
    SmileMapperConfigurator
>
{
    /*
    /**********************************************************************
    /* Context configuration
    /**********************************************************************
     */

    /**
     * Injectable context object used to locate configured
     * instance of {@link ObjectMapper} to use for actual
     * serialization.
     */
    @Context
    protected Providers _providers;

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    /**
     * Default constructor, usually used when provider is automatically
     * configured to be used with JAX-RS implementation.
     */
    public JacksonSmileProvider() {
        this(null, null);
    }

    public JacksonSmileProvider(SmileMapper mapper)
    {
        this(mapper, null);
    }
    
    /**
     * Constructor to use when a custom mapper (usually components
     * like serializer/deserializer factories that have been configured)
     * is to be used.
     *
     * @param aiOverride AnnotationIntrospector to override default with, if any
     */
    public JacksonSmileProvider(SmileMapper mapper,
            AnnotationIntrospector aiOverride)
    {
        super(new SmileMapperConfigurator(mapper, aiOverride));
    }

    /**
     * Method that will return version information stored in and read from jar
     * that contains this class.
     */
    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************************
    /* Abstract method impls
    /**********************************************************************
     */

    /**
     * Helper method used to check whether given media type
     * is Smile type or sub type.
     * Current implementation essentially checks to see whether
     * {@link MediaType#getSubtype} returns
     * "smile" or something ending with "+smile".
     */
    @Override
    protected boolean hasMatchingMediaType(MediaType mediaType)
    {
        /* As suggested by Stephen D, there are 2 ways to check: either
         * being as inclusive as possible (if subtype is "smile"), or
         * exclusive (major type "application", minor type "smile").
         * Let's start with inclusive one, hard to know which major
         * types we should cover aside from "application".
         */
        if (mediaType != null) {
            // Ok: there are also "xxx+smile" subtypes, which count as well
            String subtype = mediaType.getSubtype();
            return SmileMediaTypes.APPLICATION_JACKSON_SMILE_TYPE.getSubtype().equalsIgnoreCase(subtype) || 
            		"smile".equalsIgnoreCase(subtype) || subtype.endsWith("+smile");
        }
        // Without a media type, let JAX-RS deal with mapping if it can.
        return false;
    }

    /**
     * Method called to locate {@link ObjectMapper} to use for serialization
     * and deserialization. If an instance has been explicitly defined by
     * {@link #setMapper} (or non-null instance passed in constructor), that
     * will be used. 
     * If not, will try to locate it using standard JAX-RS
     * {@link ContextResolver} mechanism, if it has been properly configured
     * to access it (by JAX-RS runtime).
     * Finally, if no mapper is found, will return a default unconfigured
     * {@link ObjectMapper} instance (one constructed with default constructor
     * and not modified in any way)
     *
     * @param type Class of object being serialized or deserialized;
     *   not checked at this point, since it is assumed that unprocessable
     *   classes have been already weeded out,
     *   but will be passed to {@link ContextResolver} as is.
     * @param mediaType Declared media type for the instance to process:
     *   not used by this method,
     *   but will be passed to {@link ContextResolver} as is.
     */
    @Override
    protected SmileMapper _locateMapperViaProvider(Class<?> type, MediaType mediaType)
    {
        SmileMapper m = _mapperConfig.getConfiguredMapper();
        if (m == null) {
            if (_providers != null) {
                ContextResolver<SmileMapper> resolver = _providers.getContextResolver(SmileMapper.class, mediaType);
                // Above should work as is, but due to this bug
                //   [https://jersey.dev.java.net/issues/show_bug.cgi?id=288]
                // in Jersey, it doesn't. But this works until resolution of
                // the issue:
                if (resolver == null) {
                    resolver = _providers.getContextResolver(SmileMapper.class, null);
                }
                if (resolver != null) {
                    m = resolver.getContext(type);
                }
            }
            if (m == null) {
                // If not, let's get the fallback default instance
                m = _mapperConfig.getDefaultMapper();
            }
        }
        return m;
    }

    @Override
    protected SmileEndpointConfig _configForReading(ObjectReader reader,
            Annotation[] annotations) {
        return SmileEndpointConfig.forReading(reader, annotations);
    }

    @Override
    protected SmileEndpointConfig _configForWriting(ObjectWriter writer,
            Annotation[] annotations) {
        return SmileEndpointConfig.forWriting(writer, annotations);
    }
}
