<?xml version="1.0"?>

<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
     <!-- no changes, since the bug was old vap files that reflected a bug where
     units were dropped when slicing.  Autoplot no longer has this bug, but to
     support old vap files, we check for this.  Unfortunately this bug cannot be
     fixed without loading the data and must be fixed within Autoplot. -->

    <!-- we drop support for old slice nodes. We could implement slices with the new filters node, but the old properties haven't been used in ages.-->
    <xsl:template match="DataSourceFilter/property[@name='transpose']"/>  <!-- drop properties -->
    <xsl:template match="DataSourceFilter/property[@name='sliceIndex']"/>  <!-- drop properties -->
    <xsl:template match="DataSourceFilter/property[@name='sliceDimension']"/>  <!-- drop properties -->

    <xsl:template match="*">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>