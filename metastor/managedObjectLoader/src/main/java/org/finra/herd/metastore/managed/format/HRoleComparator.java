package org.finra.herd.metastore.managed.format;

import java.util.Comparator;

public class HRoleComparator implements Comparator<HRoles> {


    @Override
    public int compare(HRoles h1, HRoles h2) {
        return h1.getPrincipalName().compareToIgnoreCase(h2.getPrincipalName());
    }
}
