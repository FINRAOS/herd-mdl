/*
 * Copyright 2018 herd-mdl contributors
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
**/
package org.tsi.mdlt.util;

import java.lang.invoke.MethodHandles;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.aws.SsmUtil;
import org.tsi.mdlt.enums.SsmParameterKeyEnum;
import org.tsi.mdlt.pojos.User;

/**
 * Ldap Utils for ldap actions, like ldap user creation/modification/deletion, list ldap entries etc
 */
public class LdapUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String BASE_DN = SsmUtil.getPlainLdapParameter(SsmParameterKeyEnum.LDAP_DN).getValue();
    private static final String HOSTNAME = SsmUtil.getPlainLdapParameter(SsmParameterKeyEnum.LDAP_HOSTNAME).getValue();
    private static final String AUTH_GROUP = SsmUtil.getPlainLdapParameter(SsmParameterKeyEnum.AUTH_GROUP).getValue();

    static {
        System.setProperty("javax.net.ssl.keyStore", "/usr/lib/jvm/jre/lib/security/cacerts");
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
    }

    private static DirContext getLdapContext(User user) throws NamingException {
        String username = user.getUsername();
        String password = user.getPassword();

        String url = String.format("ldaps://%s:636", HOSTNAME);
        String conntype = "simple";
        String adminDN = String.format("cn=%s,%s", username, BASE_DN);

        Hashtable<String, String> environment = new Hashtable();

        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, url);
        environment.put(Context.SECURITY_AUTHENTICATION, conntype);
        environment.put(Context.SECURITY_PRINCIPAL, adminDN);
        environment.put(Context.SECURITY_CREDENTIALS, password);

        DirContext ldapContext = new InitialDirContext(environment);
        LOGGER.info("Bind successful");
        return ldapContext;
    }

    /**
     * Delete ldap user from ldap entry
     *
     * @param userId ldap user id
     * @throws NamingException
     */
    public static void deleteEntry(String userId) throws NamingException {
        String entryDN = constructEntryCn(userId);
        DirContext ldapContext = getLdapContext(User.getLdapAdminUser());
        ldapContext.unbind(entryDN);
    }

    /**
     * add ldap user to ldap AD group
     *
     * @param userId    ldap user id
     * @param groupName ldap AD group name
     * @throws NamingException
     */
    public static void addUserToGroup(String userId, String groupName) throws NamingException {
        modifyAttributes(userId, groupName, LdapContext.ADD_ATTRIBUTE);
    }

    /**
     * remove ldap user from ldap AD group
     *
     * @param userId    ldap user id
     * @param groupName ldap AD group
     * @throws NamingException
     */
    public static void removeUserFromGroup(String userId, String groupName) throws NamingException {
        modifyAttributes(userId, groupName, LdapContext.REMOVE_ATTRIBUTE);
    }

    private static void modifyAttributes(String userId, String groupName, int modOp) throws NamingException {
        DirContext ldapContext = getLdapContext(User.getLdapAdminUser());
        String entryDN = constructEntryCn(userId);
        String groupDn = String.format("cn=%s,%s,%s", groupName, "ou=Groups", BASE_DN);

        BasicAttribute member = new BasicAttribute("member", entryDN);
        Attributes atts = new BasicAttributes();
        atts.put(member);
        ldapContext.modifyAttributes(groupDn, modOp, atts);
    }

    /**
     * create ldap user with provided user id and user password
     *
     * @param user new ldap user to create
     * @throws NamingException
     */
    public static void addEntry(User user) throws NamingException {
        Attribute userCn = new BasicAttribute("cn", user.getUsername());
        Attribute userSn = new BasicAttribute("sn", "null");
        Attribute userUserPassword = new BasicAttribute("userPassword", user.getPassword());
        //ObjectClass attributes
        Attribute objectClass = new BasicAttribute("objectClass");
        objectClass.add("inetOrgPerson");

        Attributes entry = new BasicAttributes();
        entry.put(userCn);
        entry.put(userSn);
        entry.put(userUserPassword);
        entry.put(objectClass);

        String entryDN = constructEntryCn(user.getUsername());
        DirContext ldapContext = getLdapContext(User.getLdapAdminUser());
        ldapContext.createSubcontext(entryDN, entry);
        LOGGER.info("Added Entry :" + entryDN);
    }

    /**
     * list ldap entries
     *
     * @throws NamingException
     */
    public static void listEntries() throws NamingException {
        DirContext context = getLdapContext(User.getLdapAdminUser());

        String searchFilter = "(objectClass=inetOrgPerson)";
        String[] requiredAttributes = {"uid", "cn", "sn"};

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(requiredAttributes);

        NamingEnumeration users;
        try {
            users = context.search(AUTH_GROUP + "," + BASE_DN, searchFilter, controls);
            while (users.hasMore()) {
                SearchResult searchResult = (SearchResult) users.next();
                Attributes attr = searchResult.getAttributes();
                String commonName = attr.get("cn").get(0).toString();
                String empNumber = attr.get("uid").get(0).toString();
                String sn = attr.get("sn").get(0).toString();
                LOGGER.info("Name = " + commonName);
                LOGGER.info("Uid = " + empNumber);
                LOGGER.info("sn = " + sn);
            }
        }
        catch (NamingException e) {
            e.printStackTrace();
        }
    }

    private static String constructEntryCn(String uid){
        return  String.format("uid=%s,%s,%s", uid, AUTH_GROUP, BASE_DN);
    }
}
