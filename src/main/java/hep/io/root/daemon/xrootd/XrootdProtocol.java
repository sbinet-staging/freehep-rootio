package hep.io.root.daemon.xrootd;

class XrootdProtocol
{
   public final static int defaultPort = 1094;
   
   final static int kXR_DataServer = 1;
   final static int kXR_LBalServer = 0;
   final static int kXR_maxReqRetry = 10;
   
   final static int  kXR_auth    =  3000;
   final static int  kXR_query   =  3001;
   final static int  kXR_chmod   =  3002;
   final static int  kXR_close   =  3003;
   final static int  kXR_dirlist =  3004;
   final static int  kXR_getfile =  3005;
   final static int  kXR_protocol=  3006;
   final static int  kXR_login   =  3007;
   final static int  kXR_mkdir   =  3008;
   final static int  kXR_mv      =  3009;
   final static int  kXR_open    =  3010;
   final static int  kXR_ping    =  3011;
   final static int  kXR_putfile =  3012;
   final static int  kXR_read    =  3013;
   final static int  kXR_rm      =  3014;
   final static int  kXR_rmdir   =  3015;
   final static int  kXR_sync    =  3016;
   final static int  kXR_stat    =  3017;
   final static int  kXR_set     =  3018;
   final static int  kXR_write   =  3019;
   final static int  kXR_admin   =  3020;
   final static int  kXR_prepare =  3021;
   final static int  kXR_statx   =  3022;
   
   final static int  kXR_ok       = 0;
   final static int  kXR_oksofar  = 4000;
   final static int  kXR_attn     = 4001;
   final static int  kXR_authmore = 4002;
   final static int  kXR_error    = 4003;
   final static int  kXR_redirect = 4004;
   final static int  kXR_wait     = 4005;
   
   public final static int kXR_ur = 0x100;
   public final static int kXR_uw = 0x080;
   public final static int kXR_ux = 0x040;
   public final static int kXR_gr = 0x020;
   public final static int kXR_gw = 0x010;
   public final static int kXR_gx = 0x008;
   public final static int kXR_or = 0x004;
   public final static int kXR_ow = 0x002;
   public final static int kXR_ox = 0x001;
   
   public final static int kXR_file = 0;
   public final static int kXR_xset = 1;
   public final static int kXR_isDir = 2;
   public final static int kXR_other = 4;
   public final static int kXR_offline = 8;
   public final static int kXR_readable = 16;
   public final static int kXR_writable = 32;
   
   public final static int kXR_compress = 1;
   public final static int kXR_delete   = 2;
   public final static int kXR_force    = 4;
   public final static int kXR_new      = 8;
   public final static int kXR_open_read= 16;
   public final static int kXR_open_updt= 32;
   public final static int kXR_async    = 64;
   public final static int kXR_refresh  = 128;
   
   public final static int kXR_cancel = 1;
   public final static int kXR_notify = 2;
   public final static int kXR_noerrs = 4;
   public final static int kXR_stage  = 8;
   public final static int kXR_wmode  = 16;
   
   final static int kXR_useruser = 0;
   final static int kXR_useradmin = 1;
}
