package io.retel.ariproxy.boundary.events;

public class StasisEvents {

  public static final String invalidEvent = "not a vaild message";

  public static final String recordingFinishedEvent =
      "{\n"
          + "   \"recording\" : {\n"
          + "      \"state\" : \"done\",\n"
          + "      \"name\" : \"c0305a2b-4b8f-473a-a26e-a742d43ac032\",\n"
          + "      \"duration\" : 13,\n"
          + "      \"target_uri\" : \"channel:1536213799.2715\",\n"
          + "      \"format\" : \"g722\"\n"
          + "   },\n"
          + "   \"asterisk_id\" : \"00:00:00:00:00:02\",\n"
          + "   \"application\" : \"test-app\",\n"
          + "   \"type\" : \"RecordingFinished\"\n"
          + "}\n";

  public static final String unknownEvent =
      "{\n"
          + "  \"type\" : \"Unknown\",\n"
          + "  \"application\" : \"test-app\",\n"
          + "  \"playback\" : {\n"
          + "     \"id\" : \"072f6484-f781-405b-8c30-0a9a4496d14d\",\n"
          + "     \"state\" : \"done\",\n"
          + "     \"target_uri\" : \"channel:1532965104.0\",\n"
          + "     \"media_uri\" : \"sound:hd/register_success\",\n"
          + "     \"language\" : \"de\"\n"
          + "  },\n"
          + "  \"asterisk_id\" : \"00:00:00:00:00:01\"\n"
          + "}";

  public static final String applicationReplacedEvent =
      "{\n"
          + "   \"asterisk_id\" : \"00:00:00:00:00:01\",\n"
          + "   \"application\" : \"test-app\",\n"
          + "   \"type\" : \"ApplicationReplaced\"\n"
          + "}\n";

  public static final String playbackFinishedEvent =
      "{\n"
          + "  \"type\" : \"PlaybackFinished\",\n"
          + "  \"application\" : \"test-app\",\n"
          + "  \"playback\" : {\n"
          + "     \"id\" : \"072f6484-f781-405b-8c30-0a9a4496d14d\",\n"
          + "     \"state\" : \"done\",\n"
          + "     \"target_uri\" : \"channel:1532965104.0\",\n"
          + "     \"media_uri\" : \"sound:hd/register_success\",\n"
          + "     \"language\" : \"de\"\n"
          + "  },\n"
          + "  \"asterisk_id\" : \"00:00:00:00:00:01\"\n"
          + "}";

  public static final String stasisStartEvent =
      "{\n"
          + "   \"channel\" : {\n"
          + "      \"state\" : \"Ring\",\n"
          + "      \"connected\" : {\n"
          + "         \"number\" : \"\",\n"
          + "         \"name\" : \"\"\n"
          + "      },\n"
          + "      \"language\" : \"en\",\n"
          + "      \"id\" : \"1532965104.0\",\n"
          + "      \"caller\" : {\n"
          + "         \"number\" : \"callernumber\",\n"
          + "         \"name\" : \"callername\"\n"
          + "      },\n"
          + "      \"accountcode\" : \"\",\n"
          + "      \"dialplan\" : {\n"
          + "         \"priority\" : 3,\n"
          + "         \"exten\" : \"10000\",\n"
          + "         \"context\" : \"default\"\n"
          + "      },\n"
          + "      \"name\" : \"PJSIP/proxy-00000000\",\n"
          + "      \"creationtime\" : \"2018-07-30T17:38:24.433+0200\",\n"
          + "      \"channelvars\"  : {\n"
          + "      	\"CALL_CONTEXT\" : \"\"\n"
          + "      }\n"
          + "   },\n"
          + "   \"asterisk_id\" : \"00:00:00:00:00:01\",\n"
          + "   \"type\" : \"StasisStart\",\n"
          + "   \"timestamp\" : \"2018-07-30T17:38:24.436+0200\",\n"
          + "   \"args\" : [],\n"
          + "   \"application\" : \"test-app\"\n"
          + "}";

  public static final String stasisStartEventWithCallContext =
      "{\n"
          + "   \"channel\" : {\n"
          + "      \"state\" : \"Ring\",\n"
          + "      \"connected\" : {\n"
          + "         \"number\" : \"\",\n"
          + "         \"name\" : \"\"\n"
          + "      },\n"
          + "      \"language\" : \"en\",\n"
          + "      \"id\" : \"1532965104.0\",\n"
          + "      \"caller\" : {\n"
          + "         \"number\" : \"callernumber\",\n"
          + "         \"name\" : \"callername\"\n"
          + "      },\n"
          + "      \"accountcode\" : \"\",\n"
          + "      \"dialplan\" : {\n"
          + "         \"priority\" : 3,\n"
          + "         \"exten\" : \"10000\",\n"
          + "         \"context\" : \"default\"\n"
          + "      },\n"
          + "      \"name\" : \"PJSIP/proxy-00000000\",\n"
          + "      \"creationtime\" : \"2018-07-30T17:38:24.433+0200\",\n"
          + "      \"channelvars\"  : {\n"
          + "      	\"CALL_CONTEXT\" : \"aCallContext\"\n"
          + "      }\n"
          + "   },\n"
          + "   \"asterisk_id\" : \"00:00:00:00:00:01\",\n"
          + "   \"type\" : \"StasisStart\",\n"
          + "   \"timestamp\" : \"2018-07-30T17:38:24.436+0200\",\n"
          + "   \"args\" : [],\n"
          + "   \"application\" : \"test-app\"\n"
          + "}";
}
