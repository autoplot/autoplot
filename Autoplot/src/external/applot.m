% This program was tested on Ubuntu 64-bit (weigel's VM)

%function applot(x,y,z,respawn)

%   common applot_common, appid

%   sep= !version.os_family eq 'Windows' ? ';' : ':'
clear all
   port = 12344;
   javahome = '/usr/bin/java';
%   if javahome eq '' then javahome= 'c:/"program files"/java/jre1.6.0_03/'

%   aphome= '/media/mini/nbprojects/virbo/VirboAutoplot/dist/'

%   jars= file_search( aphome+'lib/*.jar' )

if (1)
    tmp = dir('lib');
    jars = strcat(1,tmp.name);
    jars = jars(5:end);
    sep = ':';
    classpath = regexprep(jars,'.jar',['.jar',sep]);
    classpath = [classpath,'Autoplot.jar'];
    com = [javahome,' -cp ',classpath,...
	   ' org.autoplot.AutoplotUI --port=',num2str(port),' &']
    system(com);

    fid = fopen('applot.bin','w');
    fwrite(fid,[1:10],'float64');
    fclose(fid);
end

connected = 0;
while (connected == 0)
  try
    x = java.net.Socket('localhost',port);
    %x = java.net.Socket('129.174.114.79',port);
    connected = 1;
  catch
    connected = 0;
  end
  pause(0.5);
end

    y = x.getOutputStream();
    yy = java.io.OutputStreamWriter(y);
    yyy = java.io.BufferedWriter(yy);
    com = sprintf('plot(''file://%s/applot.bin'')\n',pwd());
%    com = sprintf('plot(''https://autoplot.org/data/autoplot.ncml'')\n');
    com = sprintf('setTitle(''Hello%f'')\n',rand(1));
    yyy.write(com);
    yyy.flush();
    com = sprintf('setTitle(''Hello%f'')\n',rand(1));
    yyy.write(com);
    yyy.flush();
%    yyy.close();