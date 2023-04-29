package org.comroid.mcsd.model;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.repo.ShRepo;
import org.comroid.mcsd.web.util.ApplicationContextProvider;
import org.springframework.core.io.ResourceLoader;

import java.io.Closeable;
import java.io.File;

import static org.comroid.mcsd.web.util.ApplicationContextProvider.get;

@Data
@Slf4j
@RequiredArgsConstructor
public abstract class ServerConnection implements Closeable {
    @NonNull
    protected Server server;
    protected Session session;

    public boolean start() {
        try {
            var con = shConnection();
            this.session = get().getBean(JSch.class)
                    .getSession(con.getUsername(), con.getHost(), con.getPort());
            session.setPassword(con.getPassword());
            session.setConfig("StrictHostKeyChecking", "no"); // todo This is bad and unsafe
            session.connect();

            return uploadRunScript() && startConnection();
        } catch (Exception e) {
            log.error("Could not start connection", e);
            return false;
        }
    }

    protected boolean uploadRunScript() throws Exception {
        try{
            var fileName = "mcsd.sh";

            if(new File(fileName).isDirectory()){
                prefix=fileName+File.separator;
            }

            JSch jsch=new JSch();
            Session session=jsch.getSession(user, host, 22);

            // username and password will be given via UserInfo interface.
            UserInfo ui=new MyUserInfo();
            session.setUserInfo(ui);
            session.connect();

            // exec 'scp -f fileName ' remotely
            fileName=fileName.replace("'", "'\"'\"'");
            fileName="'"+fileName+"'";
            String command="scp -f "+fileName;
            Channel channel=session.openChannel("exec");
            ((ChannelExec)channel).setCommand(command);

            // get I/O streams for remote scp
            OutputStream out=channel.getOutputStream();
            InputStream in=channel.getInputStream();

            channel.connect();

            byte[] buf=new byte[1024];

            // send '\0'
            buf[0]=0; out.write(buf, 0, 1); out.flush();

            while(true){
                int c=checkAck(in);
                if(c!='C'){
                    break;
                }

                // read '0644 '
                in.read(buf, 0, 5);

                long filesize=0L;
                while(true){
                    if(in.read(buf, 0, 1)<0){
                        // error
                        break;
                    }
                    if(buf[0]==' ')break;
                    filesize=filesize*10L+(long)(buf[0]-'0');
                }

                String file=null;
                for(int i=0;;i++){
                    in.read(buf, i, 1);
                    if(buf[i]==(byte)0x0a){
                        file=new String(buf, 0, i);
                        break;
                    }
                }

                //System.out.println("filesize="+filesize+", file="+file);

                // send '\0'
                buf[0]=0; out.write(buf, 0, 1); out.flush();

                // read a content of fileName
                fos=new FileOutputStream(prefix==null ? fileName : prefix+file);
                int foo;
                while(true){
                    if(buf.length<filesize) foo=buf.length;
                    else foo=(int)filesize;
                    foo=in.read(buf, 0, foo);
                    if(foo<0){
                        // error
                        break;
                    }
                    fos.write(buf, 0, foo);
                    filesize-=foo;
                    if(filesize==0L) break;
                }
                fos.close();
                fos=null;

                if(checkAck(in)!=0){
                    System.exit(0);
                }

                // send '\0'
                buf[0]=0; out.write(buf, 0, 1); out.flush();
            }

            session.disconnect();

            System.exit(0);
        }
        catch(Exception e){
            System.out.println(e);
            try{if(fos!=null)fos.close();}catch(Exception ee){}
        }
    }

    public ShConnection shConnection() {
        return get()
                .getBean(ShRepo.class)
                .findById(server.getShConnection())
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, server.getShConnection()));
    }

    @Override
    public void close() {
        session.disconnect();
    }

    protected abstract boolean startConnection() throws Exception;
}
