#!/usr/bin/perl

# See http://autoplot.org/webstart_guide for syntax

# Symlink to a directory named jnlp with subdirectories of releases
# must exist in same directory as this cgi script. (Local path to this
# directory: http://autoplot.org/jnlp).

# Apache configuration
# RewriteRule ^/autoplot.jnlp(.*)$ /cgi-bin/jnlp2.cgi$1 [R]
# RewriteRule ^/autoplot.jnlp$ /cgi-bin/jnlp2.cgi [R]
 
#use strict;
use CGI qw(:standard escapeHTML);

# Everything following the ? in the URL
my $url = $ENV{'QUERY_STRING'};

# Replace depreciated uri= with open=
$url =~ s/uri\=/open\=/;

# Allow only version=VERSION to be specified with no other arguments
if ($url =~ m/^version\=/) {
    if ($url !~ m/\&/) { 
	$url = $url .  "&open=";
    }
}

# Temporarily replace template= (used by pngwalk) with open=
$url =~ s/template\=/open\=/;

#print "url-->". $url . "\n";

# Create ?open=URI query string if only URI given
if ($url !~ m/open\=/) {
    if ($url =~ m/version\=/) { # ?version=VERSION&URI
	$url =~ s/&(.*)/&open=$1/;
    } else {
	if ($url =~ m#:#) { # Assume all Autoplot URIs will have :
	    $url = "open=" . $url;
	} else {            # no URI given, just command line arguments
	    $url = $url . "&open=" ;
	}
    }
}
#print "url-->". $url . "\n";

$uri = $url;
$url =~ s/(.*)open\=.*/$1/; # Strip off URI to pass to Autoplot
#print "url-->". $url . "\n";

$uri =~ s/.*open\=(.*)/$1/; # Extract URI to pass to Autoplot
#print "uri-->". $uri . "\n";

$uri = URLDecode($uri);
# URLDecode will change vap+cdf:http:// etc to vap cdf:http://
$uri =~ s/(.*)\s(.*):(.*)/$1+$2:$3/;

$tmptext = "<argument>--nativeLAF</argument>";

if ($uri) {
    if ($url =~ m/\.pngwalk\./) {
	$tmptext = "<argument>--template=$uri</argument>";
    } else {
	$tmptext = "<argument>--open=$uri</argument>";
    }
}

@pairs = split(/&/, $url);
foreach $pair (@pairs) {
    ($name, $value) = split(/=/, $pair);
    $FORM{$name} = $value;
    if ( ($name !~ "main-class") && ($name !~ "version") && ($name !~ "max-heap-size") ) {
	$tmptext = $tmptext . "<argument>--$name=$value</argument>";
    }
}

#$nin = scalar(keys %FORM); # Number of input parameters
#print $tmptext;

if ($FORM{"version"}) {
    $dir = $FORM{"version"};
    $icon = true; # If specific version is requested, create an icon.
} else {
    $dir = "latest";
    $icon = false;
}
if (!$dir) {
    $dir = "latest";
}

# Directories have names corresponding to version
$file = "jnlp/" . $dir . "/autoplot.jnlp";

local( $/, *GFH );
open( GFH, $file) or die "Error opening autoplot.jnlp for read.\n";
$text = <GFH>;
close(GFH);

if ($dir =~ m/hudson/) {
#    $text =~ s# <property name="autoplot.default.bookmarks" value="http://autoplot.org/data/demos.xml" /># <property name="autoplot.default.bookmarks" value="http://autoplot.org/data/hudson.xml" />#;
}

if (!$icon) {
    $text =~ s#<shortcut online="true">#<shortcut online="false">#;
}

# Create <application-desc> block
if ($FORM{"main-class"}) {
    $application_desc = "<application-desc main-class=\"";
    $application_desc .= $FORM{"main-class"} . "\">";
} else {
    $application_desc = "<application-desc main-class=\"";
    $application_desc .= "org.virbo.autoplot.AutoplotUI" . "\">";
}

if ($url | $uri) {
    $text =~ s#<!--<argument>.*</argument>-->#$tmptext#;
}

#print "arg-->" . $tmptext;

$application_desc .= $tmptext . "</application-desc>";

# Replace existing application-desc
$text =~ s#<application-desc.*</application-desc>#$application_desc#s;

# http://lopica.sourceforge.net/faq.html "One trick is to make sure not to include the href attribute in the JNLP file that your servlet sends back to Web Start. This will tell Web Start to disable the update check on JNLP files, and Web Start will not treat each new JNLP file as an application update - only updated jar files will." 
$text =~ s#href=\"autoplot.jnlp\"##;

# Change vendor string
$text =~ s#VxOware#http://virbo.org/#;

# change max allowed memory.  Note Windows has a limit on allowed memory of 1G.
if ( $FORM{"max-heap-size"} ) {
   $text =~ s#1024m#$FORM{"max-heap-size"}#;
}

print header( -TYPE        => "application/x-java-jnlp-file",
	      -Content_Disposition => "attachment;filename=\"autoplot.jnlp\"",
	      -cache_control=>"no-cache, no-store, must-revalidate");



print $text;

# From http://meyerweb.com/eric/tools/dencoder/
sub URLDecode {
    my $theURL = $_[0];
    $theURL =~ tr/+/ /;
    $theURL =~ s/%([a-fA-F0-9]{2,2})/chr(hex($1))/eg;
    $theURL =~ s/<!--(.|\n)*-->//g;
    return $theURL;
}

