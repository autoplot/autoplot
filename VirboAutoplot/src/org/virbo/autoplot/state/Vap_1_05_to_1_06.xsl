<?xml version="1.0"?>

<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
     <!-- no changes, since the bug was old vap files that reflected a bug where
     units were dropped when slicing.  Autoplot no longer has this bug, but to
     support old vap files, we check for this.  Unfortunately this bug cannot be
     fixed without loading the data and must be fixed within Autoplot. -->

    <xsl:template match="*">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>