package org.codehaus.groovy.grails.plugins.orm.auditable

import grails.util.Holders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher

import javax.servlet.http.HttpSession

/**
 * Provides simple AuditLogListener utilities that
 * can be used as either templates for your own extensions to
 * the plugin or as default utilities.
 *
 * TODO: write a howto for the grails website on how to set up a closure
 * for the AuditLogListener and feed it to the Listener's configurator...
 *
 * @author Shawn Hartsock
 */
class AuditLogListenerUtil {
    /**
     * Returns true for auditable entities, false otherwise.
     *
     * Domain classes can use the 'isAuditable' attribute to provide a closure
     * that will be called in order to determine instance level auditability. For example,
     * a domain class may only be audited after it becomes Final and not while Pending.
     */
    static boolean isAuditableEntity(domain, String eventName) {
        if (Holders.config.auditLog.disabled) {
            return false
        }

        // Null or false is not auditable
        def auditable = getAuditable(domain)
        if (!auditable) {
            return false
        }

        // If we have a map, see if we have an instance-level closure to check
        if (auditable instanceof Map) {
            def map = auditable as Map
            if (map?.containsKey('isAuditable')) {
                return map.isAuditable.call(eventName, domain)
            }
        }

        // Anything that get's this far is auditable
        return true
    }

    /**
     * The static auditable attribute for the given domain class or null if none exists
     */
    static getAuditable(domain) {
        def cpf = ClassPropertyFetcher.forClass(domain.class)
        cpf.getPropertyValue('auditable')
    }

    /**
     * If auditable is defined as a Map, return it otherwise return null
     */
    static Map getAuditableMap(domain) {
        def auditable = getAuditable(domain)
        auditable && auditable instanceof Map ? auditable as Map : null
    }

    /**
     * The original getActor method transplanted to the utility class as
     * a closure. The closure takes two arguments one a RequestAttributes object
     * the other is an HttpSession object.
     *
     * These are strongly typed here for the purpose of documentation.
     */
    static Closure actorDefaultGetter = { GrailsWebRequest request, HttpSession session ->
        def actor = request?.remoteUser

        if(!actor && request.userPrincipal) {
            actor = request.userPrincipal.getName()
        }

        if(!actor && delegate.sessionAttribute) {
            log.debug "configured with session attribute ${delegate.sessionAttribute} attempting to resolve"
            actor = session?.getAttribute(delegate.sessionAttribute)
            log.trace "session.getAttribute('${delegate.sessionAttribute}') returned '${actor}'"
        }

        if(!actor && delegate.actorKey) {
            log.debug "configured with actorKey ${actorKey} resolve using request attributes "
            actor = resolve(attr, delegate.actorKey, delegate.log)
            log.trace "resolve on ${delegate.actorKey} returned '${actor}'"
        }
        return actor
    }

    /**
     * The resolve method was my attempt at being clever. It would attempt to
     * resolve the attribute you wanted from the RequestAttributes object passed
     * to it. This did not always work for people since on some designs
     * users are not *always* logged in to the system.
     *
     * It was originally assumed that if you *were* generating events then you
     * were logged in so the problem of null session.user did not come up in testing.
     */
    static resolve(attr, str, log) {
        def tokens = str?.split("\\.")
        def res = attr
        log.trace "resolving recursively ${str} from request attributes..."
        tokens.each {
            log.trace "\t\t ${it}"
            try {
                if(res) {
                    res = res."${it}"
                }
            }
            catch(MissingPropertyException mpe) {
                log.debug """\
AuditLogListener:

You attempted to configure a request attribute named '${str}' and
${AuditLogListenerUtil.class} attempted to dynamically resolve this from
the servlet context session attributes but failed!

Last attribute resolved class ${res?.getClass()} value ${res}
"""
                res = null
            }
        }
        return res?.toString() ?: null
    }
}
