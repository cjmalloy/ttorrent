package jd.plugins.hoster;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.http.Request;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.download.DownloadInterface;
import jd.utils.locale.JDL;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.PatternLayout;
import org.appwork.utils.net.throttledconnection.MeteredThrottledInputStream;

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.Client.ClientState;
import com.turn.ttorrent.client.SharedTorrent;

@HostPlugin(revision = "$Revision: 1 $", interfaceVersion = 2, names = { "bittorrent" }, urls = { "file:///.*\\.torrent" }, flags = { 0 })
public class TorrentFile extends PluginForHost {
    private static final String WAIT_HOSTERFULL  = "WAIT_HOSTERFULL";

    private static final String SSL_CONNECTION   = "SSL_CONNECTION2";

    private static final String HTTPS_WORKAROUND = "HTTPS_WORKAROUND";

    private Client              client           = null;

    @SuppressWarnings("deprecation")
    public TorrentFile(PluginWrapper wrapper) {
        super(wrapper);
        this.setConfigElements();
    }

    // @Override
    public boolean hasConfig() {
        return true;
    }

    private void setConfigElements() {

       // getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SPINNER, this.getPluginConfig(), TorrentFile.SSL_CONNECTION, "10", 1, 1, 1).setDefaultValue(16));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), TorrentFile.HTTPS_WORKAROUND, JDL.L("plugins.hoster.rapidshare.com.https", "Use HTTPS workaround for ISP Block")).setDefaultValue(false));
        /* caused issues lately because it seems some ip's are sharedhosting */
        // this.config.addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX,
        // this.getPluginConfig(), Rapidshare.PRE_RESOLVE,
        // JDL.L("plugins.hoster.rapidshare.com.resolve",
        // "Use IP instead of hostname")).setDefaultValue(false));

        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), TorrentFile.WAIT_HOSTERFULL, JDL.L("plugins.hoster.rapidshare.com.waithosterfull", "Wait if all FreeUser Slots are full")).setDefaultValue(true));

    }

    @Override
    public String getAGBLink() {
        return null;
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink parameter) throws Exception {
        return new File(getFileName(parameter.getDownloadURL())).exists() ? AvailableStatus.TRUE : AvailableStatus.FALSE;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        download(downloadLink.getDownloadURL(), downloadLink);
    }

    private void download(String downloadURL, DownloadLink downloadLink) {
        try {
            if (client == null) {
                Client.setInputStreamWrapperClass(MeteredThrottledInputStream.class);
                BasicConfigurator.configure(new ConsoleAppender(new PatternLayout("%d [%-25t] %-5p: %m%n")));

                client = new Client(InetAddress.getByName(System.getenv("HOSTNAME")), SharedTorrent.fromFile(new File(getFileName(downloadURL)), new File(downloadLink.getFilePackage().getDownloadDirectory())));

                this.dl = new TorrentDownloadInterface(this, downloadLink, null);
                this.dl.setResume(true);
                this.dl.setChunkNum(this.client.getTorrent().getPieceCount());

                downloadLink.setDownloadSize(client.getTorrent().getSize());
                if (!client.getTorrent().isMultifile()) downloadLink.setName(client.getTorrent().getName());
            }

            client.download();
            downloadLink.getLinkStatus().addStatus(LinkStatus.DOWNLOADINTERFACE_IN_PROGRESS);

            long before = 0;
            long last = 0;
            long lastTime = System.currentTimeMillis();

            while (true) {
                if (client.getState() == ClientState.DONE || client.getState() == ClientState.ERROR) break;

                downloadLink.setDownloadCurrent(client.getTorrent().getDownloaded());
                if (System.currentTimeMillis() - lastTime > 1000) {
                    last = client.getTorrent().getDownloaded();
                    // speed = ((last - before) / (System.currentTimeMillis() -
                    // lastTime)) * 1000l;
                    lastTime = System.currentTimeMillis();
                    before = last;
                    downloadLink.requestGuiUpdate();
                    downloadLink.setChunksProgress(new long[] { last });
                }

                synchronized (this) {
                    try {
                        this.wait(10 * 1000);
                    } catch (InterruptedException e) {

                    }
                }
            }

            if (client.getState() == ClientState.DONE) downloadLink.getLinkStatus().addStatus(LinkStatus.FINISHED);

            client.stop();

        } catch (Exception e) {
            downloadLink.getLinkStatus().addStatus(LinkStatus.ERROR_DOWNLOAD_FAILED);
        }
    }

    private String getFileName(String downloadURL) {
        if (downloadURL.startsWith("file:///")) return downloadURL.substring("file:///".length());

        if (downloadURL.startsWith("file://")) return downloadURL.substring("file://".length());

        return downloadURL;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    class TorrentDownloadInterface extends DownloadInterface {
        public TorrentDownloadInterface(PluginForHost plugin, DownloadLink downloadLink, Request request) throws IOException, PluginException {
            super(plugin, downloadLink, request);
        }

        @Override
        protected void onChunksReady() {
        }

        @Override
        protected void setupChunks() throws Exception {
        }

        @Override
        protected boolean writeChunkBytes(Chunk chunk) {
            return false;
        }

        //@Override
        public synchronized void stopDownload() {
            if (client != null) client.stop();
        }
    }

}
