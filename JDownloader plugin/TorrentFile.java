package jd.plugins.hoster;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import jd.plugins.download.DownloadInterface.Chunk;
import jd.utils.locale.JDL;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.PatternLayout;
import org.appwork.utils.net.throttledconnection.MeteredThrottledInputStream;

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.Client.ClientState;
import com.turn.ttorrent.client.SharedTorrent;

@HostPlugin(revision = "$Revision: 10000 $", interfaceVersion = 2, names = { "bittorrent" }, urls = { "file:///.*\\.torrent" }, flags = { 0 })
public class TorrentFile extends PluginForHost {
    private static final String WAIT_HOSTERFULL = "WAIT_HOSTERFULL";

    private static final String SSL_CONNECTION = "SSL_CONNECTION2";

    private static final String HTTPS_WORKAROUND = "HTTPS_WORKAROUND";

    private Client client = null;

    private Long speed = new Long(0);

    public TorrentFile(PluginWrapper wrapper) {
        super(wrapper);
        this.setConfigElements();
    }

    // @Override
    public boolean hasConfig() {
        return true;
    }

    private void setConfigElements() {
        config.addEntry(new ConfigEntry(ConfigContainer.TYPE_SPINNER, getPluginConfig(), "MAX_UPLOAD_KB", JDL.L("plugins.hoster.bittorrent.max_upload_kb", "Max Upload (kb)"), 0, 500).setDefaultValue(20).setStep(1));
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
                this.dl.setResume(false);

                downloadLink.setDownloadSize(client.getTorrent().getSize());
                if (!client.getTorrent().isMultifile()) downloadLink.setName(client.getTorrent().getName());

                Chunk ch = dl.new Chunk(0, 0, null, null) {

                    @Override
                    public long getSpeed() {
                        return speed;
                    }
                };
                ch.setInProgress(true);
                dl.getChunks().add(ch);
            }

            client.download();
            downloadLink.getLinkStatus().addStatus(LinkStatus.PLUGIN_IN_PROGRESS);

            long before = 0;
            long last = 0;
            long lastTime = System.currentTimeMillis();

            while (true) {
                if (client.getState() == ClientState.DONE || client.getState() == ClientState.ERROR) break;

                if (client.getState() == ClientState.SHARING) {

                    if (System.currentTimeMillis() - lastTime > 1000) {

                        int statusToSet = speed >= 0 ? LinkStatus.DOWNLOADINTERFACE_IN_PROGRESS : LinkStatus.PLUGIN_IN_PROGRESS;

                        if (downloadLink.getLinkStatus().getLatestStatus() != statusToSet) downloadLink.getLinkStatus().addStatus(statusToSet);

                        downloadLink.setDownloadCurrent(client.getTorrent().getDownloaded());

                        last = client.getTorrent().getDownloaded();
                        speed = ((last - before) / (System.currentTimeMillis() - lastTime)) * 1000l;
                        lastTime = System.currentTimeMillis();
                        before = last;

                        String info = String.format("BitTorrent client %s, %d/%d peers, %d/%d/%d pieces", client.getState().name(), client.getConnectedPeerCount(), client.getAllPeerCount(), client.getTorrent().getCompletedPieces().cardinality(), client.getTorrent().getAvailablePieces().cardinality(), client.getTorrent().getPieceCount());

                        downloadLink.getLinkStatus().setStatusText(info);
                        downloadLink.setChunksProgress(new long[] { last });
                        downloadLink.requestGuiUpdate();
                    }

                    synchronized (this) {
                        try {
                            this.wait(10 * 1000);
                        } catch (InterruptedException e) {

                        }
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

        // @Override
        public synchronized void stopDownload() {
            if (client != null) client.stop();
        }

        @Override
        public synchronized boolean externalDownloadStop() {
            if (client != null) client.stop();
            return true;
        }

    }

    static class TorrentMeteredThrottledInputStream extends InputStream {
        public TorrentMeteredThrottledInputStream(InputStream is) {

        }

        @Override
        public int read() throws IOException {
            // TODO Auto-generated method stub
            return 0;
        }
    }
}
