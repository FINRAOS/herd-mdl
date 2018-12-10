RBAC, Authorizations and Security in MDL
=========================================


Role-based access control ([RBAC](https://en.wikipedia.org/wiki/Role-based_access_control)) in Herd is managed in the following two ways:

1. <b>LDAP based auth-groups</b>: Leverages a delegation model whereby Herd is provided information via the HTTP header on the identity of the user and the
   access groups they are part of. Users are assigned auth-groups as part of the authentication process and Herd maps auth-groups to REST end points to provide RBAC.
   
2. <b>Namespace-based permissions</b>: A more granular security model where operations are authorized at the Namespace level. All access to data in Herd is controlled by the calling user's authorization to data in that Namespace.
   This authorization mapping in maintained in Herd and is readable/modifiable via CRUD end-points.  
   

### Auth-Groups

There are 4 auth groups created in the MDL stack's OpenLDAP instance, all of these are mapped to REST end-points with the 'role(s)' they are assigned in the Herd DataBase and the app 
performs a look-up when authorizing a user based off of these roles. 

|   **Auth Group**   | **Members** |   **Authorizations in Herd**  |
|       ----         |    ----     |              ----             |
| APP_MDL_ACL_RO_herd_admin | admin_user | Read/Write/Admin Services |
| APP_MDL_ACL_RO_mdl_rw | mdl_user | Read/Write Services |
| APP_MDL_ACL_RO_sec_market_data_rw | sec_user | Read/Write Services |
| APP_MDL_ACL_RO_herd_ro | ro_user | Read Services |


### Users and authorizations

There are 5 users which get created in your MDL stack for RBAC demonstration purposes - the table below lists each of those users and their respective authorizations. 
Please note that Users can be added/deleted/modified by using the _manageLdap.sh_ script as descibed in the [manage OpenLdap](admin-guide.md#managing-openldap-users-and-groups) section.                             

|   **User Id**    |   **Authorizations in Herd**    |    **Authorizations in BDSQL**    |   **SSM Parameter* name for Password**    |
| ----- | ----- | ----- | ----- |
| admin_user | Read/Write/Admin Services <br> All Namespaces | All data schemas <br> Read/Write own user schema | {ssm-prefix}/HerdAdminPassword
| mdl_user | Read/Write Services <br> MDL Namespace | Read MDL schema <br> Read/Write own user schema | {ssm-prefix}/HerdMdlPassword 
| sec_user | Read/Write Services <br> All Namespaces | Read SEC_MARKET_DATA schema <br> Read/Write own user schema | {ssm-prefix}/HerdSecPassword	
| ro_user | Read/Write Services <br> All Namespaces | Read MDL schema <br> Read SEC_MARKET_DATA schema <br> Read/Write own user schema  | {ssm-prefix}/HerdRoUserPassword 
| basic_user | Read open REST services <br> No Namespaces | Not authorized to use BDSQL | {ssm-prefix}/HerdBasicUserPassword

<sup>*replace `{ssm-prefix}` in the password param-names with `/app/MDL/${MDLInstanceName}/${Environment}/LDAP/Password` </sup>


### HERD - a unified access-control provider

HERD provides centralized access-control on data at the namespace level across the MDL ecosystem. All access to data in Herd is controlled by the calling user's authorization to data in that Namespace, 
this applies to all access points across MDL: HERD REST APIs, HERD-UI and BDSQL. 

#### Namespace level permissions in HERD

  * A user may be granted permissions to READ, WRITE, EXECUTE, or GRANT data in a set of Namespaces. 
  * These authorizations restrict access to all operations including:
      Meta-data services like reading and writing Business Object Definitions, Business Object Formats, and Business Object Data in a given Namespace
      Other services such as Clusters, Jobs, and Notifications owned by a given Namespace.
  * If not authorized, the services will return an appropriate HTTP error response such as 403 Forbidden.
 
#### Namespace level permissions in BDSQL
 
  * Any user which has READ permissions to a Namespace in HERD translates to them having SELECT privileges on *all* tables under the corresponding schema for that Namespace in BDSQL.
  > Note: Users *must* have READ permissions to a Namespace to be granted SELECT privilege in BDSQL, there is no implicit permission-mapping.
  
  * SELECT privileges are granted/revoked automatically in BDSQL when the corresponding namespace-permission is modified in HERD, this normally takes only a couple of minutes.
  * Any namespace which has *global-read* in HERD results in *all* users having SELECT privilege to its corresponding schema (and all tables) in BDSQL. 
  