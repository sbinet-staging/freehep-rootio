package hep.io.root.daemon.xrootd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Enumerate the contents of a directory.
 * @author tonyj
 */
class DirListOperation extends Operation<List<String>> {

    /**
     * Create the DirListOperation.
     * @param path is the path of a directory whose entries are to be listed.
     */
    DirListOperation(String path) {
        super("dirList", new DirListMessage(path), new DirListCallback());
    }

    private static class DirListMessage extends Message {

        DirListMessage(String path) {
            super(XrootdProtocol.kXR_dirlist, path);
        }
    }

    private static class DirListCallback extends Callback<List<String>> {

        private List<String> dirListResult = new ArrayList<String>();

        List<String> responseReady(Response response) throws IOException {
            String files = response.getDataAsString();
            int pos = 0;
            for (int i = 0; i < files.length(); i++) {
                char c = files.charAt(i);
                if (c == '\n' || c == '\0') {
                    dirListResult.add(files.substring(pos, i));
                    if (c == '\0') {
                        break;
                    }
                    pos = i + 1;
                }
            }
            return dirListResult;
        }

        @Override
        public void clear() {
            dirListResult.clear();
        }
    }
}
