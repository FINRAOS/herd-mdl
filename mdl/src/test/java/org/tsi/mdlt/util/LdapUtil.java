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
    private static final String HOSTNAME = SsmUtil.getPlainLdapParameter(SsmParameterKeyEnum.LDAP_HOSTNAME).getValue() + ".ec2.internal";

    private static final String OU_PEOPLE = "People";
    private static final String OU_GROUPS = "Groups";

    private static final String DOMAIN_NAME = "cloudfjord.com";

    static {
        System.setProperty("javax.net.ssl.keyStore", "/usr/lib/jvm/jre/lib/security/cacerts");
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
    }

    /**
     * Create ldap AD group and add user to newly created AD group
     *
     * @param adGroupName ldap AD group name to create
     * @param userId      uid of existing ldap user to be added to newly created AD group
     * @throws NamingException
     */
    public static void createAdGroup(String adGroupName, String userId) throws NamingException {
        DirContext ldapContext = getLdapContext(User.getLdapAdminUser());
        String groupDn = constructGroupDn(adGroupName, OU_GROUPS);
        String memberDn = constructEntryCn(userId, OU_PEOPLE);

        //Create attributes to be associated with the new group
        Attributes attrs = new BasicAttributes(true);
        Attribute objclass = new BasicAttribute("objectClass");
        objclass.add("top");
        objclass.add("groupOfNames");
        attrs.put("cn", adGroupName);
        attrs.put(objclass);
        BasicAttribute member = new BasicAttribute("member", memberDn);
        attrs.put(member);

        ldapContext.createSubcontext(groupDn, attrs);
        LOGGER.info("Created group: " + adGroupName);
    }

    /**
     * delete ldap AD group with provided group name
     *
     * @param groupName ldap AD group name to delete
     * @throws NamingException
     */
    public static void deleteAdGroup(String groupName) throws NamingException {
        LOGGER.info(String.format("Remove AD group: %s", groupName));
        DirContext ldapContext = getLdapContext(User.getLdapAdminUser());
        String groupDn = constructGroupDn(groupName, OU_GROUPS);
        ldapContext.unbind(groupDn);
    }

    private static void createOu(String ou) throws NamingException {
        DirContext ldapContext = getLdapContext(User.getLdapAdminUser());
        Attributes attrs = new BasicAttributes(true);
        Attribute objclass = new BasicAttribute("objectClass");
        objclass.add("top");
        objclass.add("organizationalUnit");
        attrs.put(objclass);
        attrs.put("ou", ou);
        ldapContext.bind(constructOuDn(ou), null, attrs);
    }

    /**
     * Delete ldap user from ldap entry
     *
     * @param userId ldap user id
     * @throws NamingException
     */
    public static void deleteEntry(String userId) throws NamingException {
        deleteEntry(userId, OU_PEOPLE);
    }

    /**
     * Delete ldap user from ldap entry
     *
     * @param userId ldap user id
     * @throws NamingException
     */
    private static void deleteEntry(String userId, String ou) throws NamingException {
        LOGGER.info(String.format("Delete user: %s", userId));
        String entryDN = constructEntryCn(userId, ou);
        DirContext ldapContext = getLdapContext(User.getLdapAdminUser());
        ldapContext.unbind(entryDN);
    }

    /**
     * add ldap user to ldap AD group
     *
     * @param userId    user id
     * @param groupName ad group name
     * @throws NamingException
     */
    public static void addUserToGroup(String userId, String groupName) throws NamingException {
        modifyAttributes(userId, OU_PEOPLE, groupName, LdapContext.ADD_ATTRIBUTE);
    }

    /**
     * add ldap user to ldap AD group
     *
     * @param userId    ldap user id
     * @param ou        ou of the user
     * @param groupName ldap AD group name
     * @throws NamingException
     */
    private static void addUserToGroup(String userId, String ou, String groupName) throws NamingException {
        LOGGER.info(String.format("Add user: %s to AD group: %s", userId, groupName));
        modifyAttributes(userId, ou, groupName, LdapContext.ADD_ATTRIBUTE);
    }

    /**
     * remove ldap user from ldap AD group
     *
     * @param userId    ldap user id
     * @param groupName ldap AD group
     * @throws NamingException
     */
    public static void removeUserFromGroup(String userId, String groupName) throws NamingException {
        LOGGER.info(String.format("Remove user: %s from AD group: %s", userId, groupName));
        removeUserFromGroup(userId, OU_PEOPLE, groupName);
    }

    /**
     * remove ldap user from ldap AD group
     *
     * @param userId    ldap user id
     * @param ouName    ou of the user
     * @param groupName ldap AD group
     * @throws NamingException
     */
    private static void removeUserFromGroup(String userId, String ouName, String groupName) throws NamingException {
        modifyAttributes(userId, ouName, groupName, LdapContext.REMOVE_ATTRIBUTE);
    }

    private static void modifyAttributes(String userId, String ou, String groupName, int modOp) throws NamingException {
        DirContext ldapContext = getLdapContext(User.getLdapAdminUser());
        String memberEntryDN = constructEntryCn(userId, ou);
        String groupDn = String.format("cn=%s,ou=%s,%s", groupName, OU_GROUPS, BASE_DN);

        BasicAttribute member = new BasicAttribute("member", memberEntryDN);
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
        String username = user.getUsername();

        Attribute userCn = new BasicAttribute("cn", user.getUsername());
        Attribute userSn = new BasicAttribute("sn", "null");
        Attribute uid = new BasicAttribute("uid", user.getUsername());

        Attribute uidNumber = new BasicAttribute("uidNumber", String.valueOf(listEntries() + 1));
        Attribute gidNumber = new BasicAttribute("gidNumber", String.valueOf(1001));
        Attribute homeDirectory = new BasicAttribute("homeDirectory", "/home/" + username);
        Attribute mail = new BasicAttribute("mail", username + "@" + DOMAIN_NAME);
        Attribute loginShell = new BasicAttribute("loginShell", "/bin/bash");

        Attribute userUserPassword = new BasicAttribute("userPassword", user.getPassword());
        //ObjectClass attributes
        Attribute objectClass = new BasicAttribute("objectClass");
        objectClass.add("inetOrgPerson");
        objectClass.add("posixAccount");

        Attributes entry = new BasicAttributes();
        entry.put(userCn);
        entry.put(userSn);
        entry.put(userUserPassword);
        entry.put(objectClass);
        entry.put(uid);

        entry.put(uidNumber);
        entry.put(gidNumber);
        entry.put(homeDirectory);
        entry.put(mail);
        entry.put(loginShell);

        String ou = user.getOu() == null ? "People" : user.getOu();
        String entryDN = constructEntryCn(user.getUsername(), ou);
        DirContext ldapContext = getLdapContext(User.getLdapAdminUser());
        ldapContext.createSubcontext(entryDN, entry);
        LOGGER.info("Added Entry :" + entryDN);
    }

        /**
         * list ldap entries
         *
         * @throws NamingException
         */
    //TODO split list Entries with get Max uidNumber
    public static int listEntries() throws NamingException {
        DirContext context = getLdapContext(User.getLdapAdminUser());
        int maxUidNumber = 10009;

        String searchFilter = "(objectClass=inetOrgPerson)";
        String[] requiredAttributes = {"uid", "cn", "sn", "uidNumber"};

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(requiredAttributes);

        NamingEnumeration users;
        try {
            users = context.search(BASE_DN, searchFilter, controls);
            while (users.hasMore()) {
                SearchResult searchResult = (SearchResult) users.next();
                Attributes attr = searchResult.getAttributes();
                String commonName = attr.get("cn").get(0).toString();
                String uniqueName = attr.get("uid").get(0).toString();
                String sn = attr.get("sn").get(0).toString();
                int uidNumber = Integer.parseInt(attr.get("uidNumber").get(0).toString());
                maxUidNumber = maxUidNumber > uidNumber ? maxUidNumber : uidNumber;
                LOGGER.info("Name = " + commonName);
                LOGGER.info("Uid = " + uniqueName);
                LOGGER.info("sn = " + sn);
                LOGGER.info("uidNumber = " + uidNumber);
            }
        }
        catch (NamingException e) {
            LOGGER.error(e.getMessage());
        }
        return maxUidNumber;
    }

    private static DirContext getLdapContext(User user) throws NamingException {
        String username = user.getUsername();
        String password = user.getPassword();

        String url = String.format("ldaps://%s:636", HOSTNAME);
        String conntype = "simple";
        String adminDN = String.format("cn=%s,%s", username, BASE_DN);

        Hashtable<String, String> environment = new Hashtable<>();

        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, url);
        environment.put(Context.SECURITY_AUTHENTICATION, conntype);
        environment.put(Context.SECURITY_PRINCIPAL, adminDN);
        environment.put(Context.SECURITY_CREDENTIALS, password);

        DirContext ldapContext = new InitialDirContext(environment);
        LOGGER.info("Ldap Bind successful");
        return ldapContext;
    }

    private static String constructEntryCn(String cn, String ou) {
        return String.format("cn=%s,ou=%s,%s", cn, ou, BASE_DN);
    }

    private static String constructGroupDn(String groupName, String ou) {
        return String.format("cn=%s,ou=%s,%s", groupName, ou, BASE_DN);
    }

    private static String constructOuDn(String ou) {
        return String.format("ou=%s,%s", ou, BASE_DN);
    }
}
