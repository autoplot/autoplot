#!/usr/bin/perl

# See http://autoplot.org/webstart_guide for syntax

# Symlink to a directory named jnlp with subdirectories of releases
# must exist in same directory as this cgi script. (Local path to this
# directory: http://autoplot.org/jnlp).

# Apache configuration
# RewriteRule ^/jnlp.cgi(.*)$ /cgi-bin/jnlp.cgi$1 [R]
# RewriteRule ^/jnlp.cgi$ /cgi-bin/jnlp.cgi [R]

#use strict;
use CGI qw(:standard escapeHTML);

# Everything following the ? in the URL
my $url  = $ENV{'QUERY_STRING'};
$url = URLDecode($url);

# URLDecode will change vap+cdf:http:// etc.
# to vap cdf:http://
$url =~ s/(.*)\s(.*):(.*)/$1+$2:$3/;

#print "$url\n";

@pairs = split(/&/, $url);
foreach $pair (@pairs) {
    ($name, $value) = split(/=/, $pair);
    $FORM{$name} = $value;
}
$nin = scalar(keys %FORM); # Number of input parameters

if ($FORM{"version"}) {
    $dir = $FORM{"version"};
    $icon = true;
} else {
    $dir = "latest";
    $icon = false;
}
if (!$dir) {
    $dir = "latest";
}

$file = "jnlp/" . $dir . "/autoplot.jnlp";

if ($FORM{"uri"}) {
    $uri = $FORM{"uri"};
} else {
    $uri = "";
}

if ($url !~ m/version|uri/) {
    $uri = $url; # jnlp.cgi?parameter
}
$uri = URLDecode($uri);

local( $/, *GFH );
open( GFH, $file) or die "Error opening autoplot.jnlp for read.\n";
$text = <GFH>;
close(GFH);

if (!$icon) {
    $text =~ s#<shortcut online="true">#<shortcut online="false">#;;
}

if ($url) {
    $text =~ s#<!--<argument>.*</argument>-->#<argument>$uri</argument>#;
} else {
    $text =~ s#<!--<argument>.*</argument>-->##;
}

# http://lopica.sourceforge.net/faq.html "One trick is to make sure not to include the href attribute in the JNLP file that your servlet sends back to Web Start. This will tell Web Start to disable the update check on JNLP files, and Web Start will not treat each new JNLP file as an application update - only updated jar files will." 
$text =~ s#href=\"autoplot.jnlp\"##;

# Change vendor string
$text =~ s#VxOware#http://virbo.org/#;

print header( -TYPE        => "application/x-java-jnlp-file",
	      -Content_Disposition => "attachement;filename=\"autoplot.jnlp\"",
	      -cache_control=>"no-cache, no-store, must-revalidate");

print $text;

sub URLDecode {
    my $theURL = $_[0];
    $theURL =~ tr/+/ /;
    $theURL =~ s/%([a-fA-F0-9]{2,2})/chr(hex($1))/eg;
    $theURL =~ s/<!--(.|\n)*-->//g;
    return $theURL;
}

