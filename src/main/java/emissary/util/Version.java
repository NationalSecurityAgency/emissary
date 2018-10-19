package emissary.util;

import java.io.IOException;
import java.io.InputStream;

import emissary.config.ConfigUtil;
import emissary.util.io.ResourceReader;

public final class Version {
    private String version = "missing version";
    private String timestamp = "missing timestamp";

    public static String mobileAgents =
            "xxxxxxxxxxxxxxxxxxxxdddddxxxxkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkOOOOOOOkOOOOOOOOOOOOOOOOOOkkxoolooo\ndddddddddddxxxxxxxxxxxxxxxxxxddddxkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkOkkkkkOOOOOOOOOOOOOOOOOOOkkkkxdo\nddddddddddddxxxxxxxxxxxxxxxxxxxxxxxxxkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkOOOOOOOOOOOOOOOOOOOOOOOOOOOOk\nkkkkkkkkkkkkkkkkkkkkkkkkkkkxxxxxxxxxxxxxkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkOOOOOkOOOOOOOOOOOOOOOOOOOOO\nkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkxdooodooddddooooooddxxxkkkkkkkkkkkkkkkkkkkkkkkOOOOOOOOOOOOOOOOOOOOO\nkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkxxddolccc::::;,,,,,,,,,;;:cloxxxkkkkkkkkkkkkkOkkkOOkkOOOOOOOOOOOOOOOOO\nkkkkkkkkkkkkkkkkkkkkkkkkkkxxdolc::;;;;;;;;;;,,,,,,,,,,,,,,;:loxxkkkkkkkkkkkOkOkkkkOOOOOOOOOOOOOOOOkO\nkkkkkkkkkkkkkkkkkkkkkkkkxoc;,,,,'',;;;;;;,;,,,;;;;,,,','',,,,:codxxkkkkkkkkkkkkkkkOOkOOOOOOOOOOOOOOO\nkkkkkkkkkkkkkkkkkkkkkxoc;'......',,,,,;;;,,,,,,,,,,,,,,,'.....',;coxkkkkkkkkkkkkkkOOkkOOOOOOOOOOOOOO\nxxxxxxxxxxkkkkkkkkxoc,'.........'''',,,,,,,,;;;;;;;;;;:::;;;;,'...,codkkkkkkkkkkkkkkkkkkkOOkkkkkkkkk\nxxxxxxxxxxxxxxxxxo;,'...........'''',,,;;;;:::::::::::::::::c:cc:,.';:lxkkkkkkkkkkkkkkkkkkkOkkkkkkkk\nxxxxxxxxxxxxxxxo:,''..........'',,,;;;:::::::::::::ccccccccccc:cccc:;;:ldxkkkkkkkkkkkkkkkkkkkkkkkkxx\nxxxxxxxxxxxxxdc,'''.........',,;;;;;::::::::::::cccccccccccccc:ccccclc:;cldkkkkkkkkkkkkkkkkkkkkkkkkk\nkkkkkkkkkkkkd:'.''.......',,;;;;;:::::::::::cccccccccccccccccccccccccll:;cldkkkkkkkkkkkkkkkkkkkxxxxx\nxkkkkkkkkkko,..''.......',;;;;;;:::::::cc::cccccccccccccccccccccccccccll:,coxkkkkkkkkkkkkkkkkxxxxxxx\nxxxxkkkkkxl,...''..'..',,;;;;;;:::::::cccccccccccccccccccccccccccccccclll:,loxkkkkkkkkkkkkkkkkkkxxxx\nxxxxxxkkkd;'.'','''',',,,;;;;::::::cccccccccccccccccccccccccccccccccccllllc:odxkxxxxxxkkkkkkkkkxxxxx\nxxxxxxxxxc'.',,,,,,;,,;;;;;;:::::::ccc:::::cccccccc:cccccccccccccccccclllllccdxxxxxxxxxxxxxxxxxxxxxx\nddddddddo;'';,;;;::;;;;;;;;;;;::::::::::::ccccccccccccccccccccccccccclllllllcoxxxdxdddddxxxxxxxxxxxx\ndddddddoo;',,;;;::;;;;::::;;;::::::::cc:ccccccccccccc:cc:ccccccccllllllllllllodxxxxxxxxxxxxxxxxxxxxx\nddddddddd:,,;;;;;;,;::::;::;;::::::::::cccccccccccccc::::::ccccclllllllllllllodxxxxkkkkkkxxxxxxxxxxx\ndddddddddc;;;;,',;;:::::::;:::::::::::::::::::ccccccc:::cc:cccccccccccccclllloxxxxxxxxkkkkkkkkkkxxxx\ndddddddool::,'..',;;::::::;;;:::;;;;;:::;;::::::::::::::::::::::c:;;::;,;;cloodxkkkkkkkkkkkkkkkkkkkk\ndddddddddoc;,'..',,;;:;;;;;;;:;;;;;,,,,,',,'.''',,,;::::::::::;;,,;;,,,,,,;:lodxkkkkkkkkkkkkkkkkkkkk\nddddxdddddl;,'.',,,;;;;;;;;;;;;;;,''''''''....'',,,,;;;;:::::::;;;,,,,,,,,,;cclxxxkkkkkkkkkkkkkkkkkk\ndddxxxxxxxd:;,,,,,,;;,,;;;;:;;;;,'''''',,,,,,,,,,,'',;;::::c:;;;,'...',;:c:cxxddlxkkkkkkkkkkkkkkkkkk\nddxxxxxxdxdc;,;;;;,;;;,;;;;::;;;,',;,,''.......'',,'''',;;;;,',,'.....,'.,clddxdl:lldkkkkkkkkkkkkkkk\ndddddddddddl:;;;;;,;;,,;;;:;;;;;,;;;'..'....''..',,''.';;:::,';;,''',,;;;:clodxkxlkkkkkkkkkkkkkkkkkk\nxdxxdxxxxddo:,,'',,,,',,,,,,,''',;;'.',;;,;;,,,',,''..';:::c:.';,,,;::ccclcloxkkdlkkkkkkkkkkkkkkkkkk\nxxxxxxxxddxl;;;;,,;;;,,;;;;;;;;;,,;,;;;;;;;;;;;,,,,'..,;;;:::,';::::ccccclclldkx:cxkkkkkkkkkkkkkkkkk\nxxxxxxxxxxd;,;;;;;;;;;;;;;;;::;;;,;:::::::::;;;;,,,'',;,,;;::::;;;;;;;::::ccclll::dkkkkkkkkkkkkkkkkk\nxxxxxdddxxo;',::;;;;;;;;;;;;;;;::::;;;;;;;;;;,,,,,''',,,;;;::::::::::;;;::ccclllc:lkkkkkkkkkkkkkkkkk\nxxxdoodddxl;,;;;:;;;;;;;;;;;;;;:::::;:;;;;;;;;;;,'',;;;;;::::ccclc:::::::::cccllc:lkkkkkkkkkkkkkkkkk\nxdooooddddo;,;::,.,;;;;;;;;;;;;;:;;;;;;;;;;;;;;,'',;;,',;::::cc::c:;;:::::ccccllc:lkkkkkkkkkkkkkkkkk\nddddddddddd:,;:;',,:;;,;;;;;;;;;;;;;;;;;;;;;;;;,,'''.....,;;:;'.';:;;;;:::ccclllc;:xkkkkkkkkkkkkkkkk\nxxxxxxdddxxo,,;,.,;;;;,;,,;;;;;;;;;;;;;;;;;;;;,,,,,,,,'','',,;:::cllllccc:cccclll::okkkkkkkkkkkkkkkk\nxxxxxxxdddddc,,,,,,,,;;;;;;;;;;;;;;;;;;;;;;;;,,,;:c:::;;,;:;,;:::cccccllolllcllllc:okkkkkkkkkkkkkkkk\nxxxxxddoooddd:,,,;:;;;;;;;;;;;;;;;;;;;;;;;;;,,;:c::::;;:c:;;,;:;;:cclllccccclllloloxkkkkkkkkkkkkkkkk\nxxxxxddoooddxxc,;;,,;;;;;;;;;;;;;;;;;;;;;;:;:;;;;:::::::;;;;,,;:c:::clllll:::cclodkkkkkkkkkkkkkkkkkO\nxxxxxxxddxxxkkxo:,,,;;;;;:;;;;;;;;;;;;;;;;;,,;:ccc::;;,,',,,,,;;;;::;::cccc:ccccldkkkkkkkkkkkkkkkkOO\nxxxxxxxxxxkkkkkkkdc:;;;;;::;;::;;,;;;;;;;;;;;;,'''..........'...'''',;,;;::ccllooxxkkkkkkkkkkkkkkOOO\nxxxxxxxxxkkkkkkkkkdoc;;;;;::;;;;;;;;;;::::::;,''........'',,,,,,;;::c:;;;::clloodkkkkkkkkkkkkkkkkkOO\nxxxxxxxxkkkkkkkkkkxdl:;;;;::;;;;;;;;;;;:::::;;,,,,,,,,,,,;;;;;;::cccc::::::cllooxkkkkkkkkkkkkkkkOkOO\nxxxxxxxxkkkkkkkkkkkxdl;;;;:::;;;;;;;;;;;::::;;;;;;;,;;;;;;;;;;::::ccc:cccccloooxkkkkkkkkkkkkkkkkkkkk\nxxxxxxxxkkkkkkkkkkkkxdl;;;::;;;;;;;;;;;:::::;;;;;;;;;;;;;;;;;;;::::::ccccclloodkkkkkkkkkkkkkkkkkkkkk\nxxxxxxxxxxkkkkkkkkkkkxdl:;;::::;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;::::ccclllllodkkkkkkkkkkkkkkkkkkkkkk\nxxxxxxxxxxxxkkkkkkkkxdddoc:;:::::;;;;:;;;;;;;;;;;;;;;;;;;;;;;;:;::::cccllllloxkkkkkkkkkkkkkkkkkkkkkk\nddddddddxxxxxxxxxxxxxdddddoc:::::;;;;;;;;;;;;;;;;;;;;;;;;;;;;:::::ccccllllldxkkkkkkkkkkkkkkkkkkkkkkk\ndddddddddxxxxxxxxxxxddddddddoc:::::;;;;;;;;;;;;;;;;;;;;;;;;:::::ccllllllllokkkkkkkkkkkkkkkkkkkkkkkkk\ndddddddddddxxxxxdddddddddxxxxddlc::::;;;;;;;;;;;;;;;;;;;;;;::::ccclllllllokkkkkkkkkkkkkkkkkkkkkkkkkk\nddddddddddddddoooooddddxxxxxxxxxxdlc:::;::;;;;:;;;;;;;;;;:::::ccccclllllokkkkkkkkkkkkkkkkkkkkkkkkkkk\nddddddddddddddddoooodxxxxxxxxxxxxxxxdoc:::;;;;;;;;;;;;;;;:::ccccllllllloxOkkkkkkkkkkkkkkkkkkkkkkkkkk\ndddddddddddddddddddoodxxxxxxxxxxxxxxxxxdolc:;;;;,;,;;;;;::::ccclclllcloxxkkxxkkkxkkkkkkkkkkkkkkkkkkk\ndddddddddddddddddddddoodxkkkkxkkkkkkxxxxxxdol:;,'''',,,;;:::ccccllcclodddxOkkxdoxxkkkkkkkkkkkkkkkkkk\nxxxxxxxxxxxxxddddddddddddxxxxkkkkkkxxxxxxoc,,;;:;,''''',,;;:::cccccoddoloxxolccccdxkkkkkkkkkkkkkkkkk\ndxxxxxxxxxxxxxxxxxxxxxxxdooxxxxxxxxxxxdddo:....,::;,''....''',,;;.'olc:cldc:c::cccldooooodxkkkkkkkkk\ndddddddxxxxxxxxxxxxxxxxxxxxddxxxxxxxxxxxxddo;,,'.'::;,''''','':ccc::;,cool:ccccccc:::::::cdxxxxxxkkk\nxxxddxxxxxxxxxxxxxxxxxxxddxxkxxkkkkxxxxxdddoo:,,'..;c:::;;;;,,:clolc;;:::ccccclllccccc:::cxkkkkkkkkk\nxxxxxxxxxxxxxxxxxxxxxxxxxxxdddxdxxkxxxxxxddddo:;,'..:ccccccclolclol:;:;;,,,:cllllllcccc::okkkkkkkkkk\nkkkkkkxxxxxxxxddxxxxxxxxxxxdddddoodxxdddddddooo:::,,:llcclclol,;loc;;:::,'',clcllllccccclkkkkkkkkkkk\nkkkkkkkkkkkkkkxxxxxxxxxxxxxxxxdddddoddddooolllllccc:;:llllllc,.,c:;;:;;;;;;:c:ccccccccccdkkkkkkkkkkk\nxkkkkkkkkkkkkkkkkkkkxxxxxxxxxxxxddddddddddoolccc;:'.;:clolll:;,coollcc::::::::::c::::::okkkkkkkkkkkk\nxxxkkkkkkkkkkkkkkkkkkkkkkkxxxxxxxxxxdddodddddolll;..;::cloool:cdxxxxxdolcccc::::::::::okkkkkkkkkkOkk\nkkkkkxxxkkkkkkkkkkkkkkkkkkkkkkkkkkkkxxxxddddooooxo:cc:::clooodxkkkkkkkxxddodddlcc::cloxkkkkkkkkkkkkk\nkkkkkkkxkxxxxxxkkkkkkkkkkkkkkkkkkkkkkkkkkkkkxxddoooddocccccoxkkkkkkkkkkxxxxdxxdddddodxkkkkkkkkkkkkkk\nkkkkkkkkkkkkkkxxxxxxkkkkkkkkkkkkkkkkkkkkkkkkkkkkxxxdddooolclkkkkkkkkkkkxxxxxxxxxxxkkkkkkkkkkkkkkkkkk\nkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkxxxkkkkkkkkkkxxxxdoxxxxxxxxxxxkkxxxkkxxxkkkkkkkkkkkkkkkkkOO\nkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkxxxxxxxxxxxkkkkkxxxxxxxxxxxxxxxxxkkxxkkkkkkkkkOOkkkkkkkkO\nkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkxxxxxxxxxkxxkkxxxxkkkkkkxkkxxkkkkkkkkkkkkkkkkkkOO\nkkkkkkkkkkkkkkkkkkkkkkkkkkkxkkkkkkkkkkkkkkkkkkkkkkkkkkkkkxxxxxdxkkkkkkkkkkkkkkxkkkkkkkkkkkkkkkkkkkkk\nkkkkkkkkkkkkkkkkkkkkkkkkkkkkkxxxxkkkkkkkkkkOkkkkkkkkkkkkkkkkkxxdkkkkkkkkkkkkkkxkkOOkkkkkkkkOkkkkkkkk\n\n\n"
                    +
                    "MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM\nMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM\"\"\"9TWMMMMMMMMHHMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM\nMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMY!``    ` ```?!`  ``    .``??TTMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM\nMMMMMMMMMMMMMMMMMMMMMMMMMMMMM\"\"!` ``        ` `````   ....   `````.`?TMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM\nMMMMMMMMMMMMMMMMMMMMMMMMM#Y:^..`..                       `````^`  `...`TMMMMMMMMMMMMMMMMMMMMMMMMMMMM\nMMMMMMMMMMMMMMMMMMMMMMMBWI!`.```             `           ``....```.. `` `?HMMMMMMMMMMMMMMMMMMMMMMMMM\nMMMMMMMMMMMMMMMMMMMM@++!`..`                              ` ....^.^^^. `   ?TMMMMMMMMMMMMMMMMMMMMMMM\nMMMMMMMMMMMMMMMMMMMI;::^```                   `         ` ...`^````  `     `^?MMMMMMMMMMMMMMMMMMMMMM\nMMMMMMMMMMMMMMMMM#:::^.. `  ..             ```  `      ..... `^``            ` ?MMMMMMMMMMMMMMMMMMMM\nMMMMMMMMMMMMMMMMM5+:;.....+^..``    ```` .      ``   `````````^...            ` ?HMMMMMMMMMMMMMMMMMM\nMMMMMMMMMMMMMMM8++++++zv!!`+.``` ```` .`.......`.` ``. `^............ `` `       `7HMMMMMMMMMMMMMMMM\nMMMMMMMMMMMMM#C!`^^.+?!...``............++++?+.............,.++^:`^.`.`  `         `?MMMMMMMMMMMMMMM\nMMMMMMMMMMMMM$!`..`...,+:+`^.?+++++++z+zzzOOzzzzzzzzzttzzlOzzz++:.^`^. ``            ?WMMMMMMMMMMMMM\nMMMMMMMMMMMMGt..!`.++++:..?;+ttlzzrrwrtrvvzuuzuwwuwwwuuuwvrvOttOz++.... `` . .        JMMMMMMMMMMMMM\nMMMMMMMMMMEJ=.^:``:^:::+?++zOrvvzzuuuuzuuuuuuuuuuuuZuZuuuuuuzzwtOOz+.... ``.`...`     `?MMMMMMMMMMMM\nMMMMMMMMMNM#`^```^...?+==+zrrvzzzuzuuuuZuZuZuZuuZZuZZZZZZZuuuuzvvrrOz++... `` .. `    ` `MMMMMMMMMMM\nMMMMMMMMMMD+.```.`.^.+zllrrrrrzzuuuuuZZZZZZZZZZZZyyZyXXXXyZuuuzzzrrtOlz+..... `..``  ``` `MMMMMMMMMM\nMMMMMMMMMM6```` ^..?+?lltrrzzuuuuuZuuuuZyZZyyZyyyyyyZZyZZZZZZuZuzzrrtl=??1++...^^.`. ```` JMMMMMMMMM\nMMMMMMMFWt .....^^`+=lltrrrvzzuuZZZyyZyyyVyyyyyVyVyyyyyyyZZZZZuuzzvrttll=??1+:+,+^.... `. JMMMMMMMMM\nMMMMMMMF``......::++ztrrtrrvvzuuuZuuZZuZuuuZZuuuZZZuuuZZuZuuuuuzuzvrrttllzz=z+;+:..  .`... dMMMMMMMM\nMMMMMMM^````....::+1ltttttrvzuzzuuuZZuZuZuuuuuuZZZuuuZuuuuuzuuuuuzvrrtllttl=??+;:.. ```` . dMMMMMMMM\nMMMMMMMr.......?;+1ltttrrrvrwwrvvwXXuuuuuuuZuZuuuuUuuzzzzzzzXzuzzuzrrtttlll==++++:`. `   `.MMMMMMMMM\nMMMMMMMr .  ^.:;?==lttrrvvvzzvzzzzuzuuuuuuuuuuuuuuZuuZZZZuuzzzzzuzvrrtttlllz+;;;++^... `  `dMMMMMMMM\nMMMMMMM\\....^:;+==l=ttrvvvvzzzzuuuZZZZuZXyyyZyyyyXyyyWyZZZuzzrOOOOrrrtttltl=?+:++:..^.. ``.,MMMMMMMM\nMMMMMMN!..^^.;;;==lltttOOOOwVZzCOXXZZZyyyyyyyyfWyW0VOVvzlz11++1z?+1zzttlltll1=++.^``:++.. .,MMMMMMMM\nMMMMMMM.`^^^`:;??=llllz?+!`:^.+++==OwZXyVyyyyVVyZZvOzz+zz;;+`:;++?+;;+==llll==??+.^^`!:+!^..MMMMMMMM\nMMMMMMMr `^^:;;?=llzz+`..++:+;????zlztwVVyVVVfVyyXvrI=zz+++uwwwwOzz++?++=lllz???++.^...^`^^+UMMMMMMM\nMMMMMMMr.^^.::???==z+.+++wwwA&&z&zzltOXXVyyVffVyyyXwuuwQQgQHWXXwOzz++++?++l=l=?+;`` ^.. ^````WMMMMMM\nMMMMMMM|..^::;??==+;?+11zOltrOwrOwzXXQmQXZZXWWyZWyXQWMBHkz;+1177COXwOlz+;+zz?!??+``` ...` `..JMMMMMM\nMMMMMM# `!:+;;?l1zzz++=OrwOC11==1=zwUXXWMHOOwQgmAwWOOzdMMHs`+`...`++zlltOz++.....        ` .+.MMMMMM\nMMMMMMF +`^`+!!!+1z;??zvz++!!``!`?dM#zttzM?OOOOO1zHzvOzZWM$!^^`,Oz.`+zttrOzwllz1?+^ ``..Jzzz+.?MMMMM\nMMMMMMF .:^` ..+jz;???zv+`.+,. ``JW0IzOZ+S+ztttlz;XvOOzzzzOzwOlllz1+++?1zrwwrll=?+.....zZuZzOz,dMMMM\nMMMMMM$.``   .++dI??==++..+zz?J&++zzzwZ1z$?=lltl=+jk+OlltOvzuuuuuzwwrOzztOwuuOll??! `.wuXXSOXyy.MMMM\nMMMMMb ......+zzzzz+z++zzzzOwwuXuzZllvzzzz1zltll=??OwzrrOrrzzvvzvrOwwXzzwwwrrOl=?;!` .wXXqWZdWXldMMM\nMMMMMF 1zlI.`?zwwwwl?ztttrrrzzuXwZOllllz+1llllzOzz??1zzyzwvrwrwwwwwzzzvrrOZXwrO=?;!.?j0OXWWkzSXIJMMM\nMMMMM| zlzt! ?1OwXuwOtOttrrrttttttOwrtOz+ltwwvwwzrO?zzOXXXXkXXzwuzzuuzzzvrrtXXtv?;!.1wVXVVWkOXwS,MMM\nMMMMMN.ywIwc `1ztwXzuzvvrwvrzvrvzwwXXVI+?zwXXWWHSurz1=11wyyVfWVXZZZZZuzzvrrtzOwz;;+?dkzdVpbHZzX%.MMM\nMMMMMB`WXIwO..+1OwwvzuuuuuzvvzwXyyV0O=11ztwrXXZuzrrrwwwOzXyyVVfpfWyyZZZuuvrtt=zI;:+.dywwyVVWkz0`.MMM\nMMMMMR.zZzrOz,+?zOrvzzuzzuuuXZyyVyXXlOrwwOOOwXuzwv??1OOwOwuXyyyVVffyyyZZuzwrtl=zz:^`zX0WVWWWSzC MMMM\nMMMMMMF`IzwuXk+;zzrrvzzzuuXZyyVfVWuwtwrw+...+Oltz+zzlwZttrvXuZZyyyVVyyZZZuuvOtlz+:^^juvXUOwuZw:.MMMM\nMMMMMM$ 1wXyZ0I+1ltrrvzuuZZyyVfyXXwOtlOOOlOrwwwwvzvtOllllttrwXXyyyVWyyyyZZuzuwO=z+^^?vOwwwV0wI..MMMM\nMMMMMMM&`zXXUSwc+ttrrvuuZZyyVVyZXwtlll=l=ltOXZuZuuuwtlllllllttwuZZyyyyyZZZuzwXwtz+::?wwXXWrX0:JHMMMM\nMMMMMMMN.?OtvOOljtttrzuZZZyyyyZXOO==???=lltwuuuXXwuzrrll=l===lzvwXuZZWyZuZZuwyXOlz.^`zXyZXXZ:.dMMMMM\nMMMMMMMR, 1Owww;ztttrzuuZZyyZuzZI1+???zltOrwzwwwwwzvrrtttllllllwzzuuuZyWXzXZzVXwlz.^^zkXuzCjMMMMMMMM\nMMMMMMMMMp`zwuXzzXltvuuuZZyZZzwl=??==zOttOtrvXXUVwrttOz=1111z1ztwzuuuZZyXuuuwyXwlz+^^jWXVCJMMMMMMMMN\nMMMMMMMMMM jXuyIzuOOzzzzZyWuzrtl======zz1lzzlttOOzzzuwOzz+++...^+wzzuuXyXvzuXyZwtl:^:```.dMMMMMMMMMM\nMMMMMMMMMMJ.jwuIjZwzzzvzXyZuzrrtlz?!++zwOXXwyWkzWHyXXXOwZttvzC+JzOrvuuyyXvzwwVXXtzz^:` .NMMMMMMMMMM#\nMMMMMMMMMMMN`?wI+wXOwzzvXyyZuzrrv+..+1Owkw0jWWH0W0UC?+!?C?!+.+zztrrwzuyZXvvXyyXXtv+:^. JdMMNMMMMMMM#\nMMMMMMMMMMMMMm,``OwwwuzvzZZyZXOtz++?z???!...++zz+z+zz1++??+?1lzrrrrrwZyuXrwyVWuwtz+:^` .dMMMMMMMMMM#\nMMMMMMMMMMMMMMMNe?rXtwzvvXXyXXtOl=1+++;++??=ltttOwOOzzzll===ztttrrOOwuuzzrwyyWuOlz::^. .WMMMMMMMMMM#\nMMMMMMMMMMMMMMMMMJzwXrvvrwuZXXrrttO=???+?zztvuuuuuuuvrttOrOtttrrrrrrrXuzzwZyyZZOz?;+:..JWMMMMMMMMMMb\nMMMMMMMMMMMMMMMMMNJOXwrvvrzuZXrrrtll=?==lltvzuuuuuzzzvvrrvrrtrrtrrrrzuzvrXyyXvOz??;:;. .dMMMMMMMMMM#\nMMMMMMMMMMMMMMMMMMP?OvrrrvvzuXttrttlzltrrvzzzvzzuzuvrvvvOwrrrtttrrrrzuZtwZyuXO???;;;;+  ,MMMMMMMMMMF\nMMMMMMMMMMMMMMMMMMN.+zOrtrrvXXOtttttltttrvwwvOOOOOttOOOrtttlttltttrrrwrwXXZXOv???++?;!.       `  ``\nMMMMMMMMMMMMMMMMMMMh.;1OvrrrOvOtttllllllltOOz=llttrttzll==llllltlttrrOOwZuuOI?;???+?+++.. `   ``````\nMMMMMMMMMMMMMMMMMMMMLJ?+Orrrtrrrtl=======l1=llttrrwvwrttzz=l=llllllltzwuuXOOz??1??????+........`...`\nMMMMMMMMMMMMMMMMMMMNd$`?+zOrrttttl=llll==l=ztrvzuuuZZXwvwrrOltttOllzzwvvzwOz??????==??+......... .``\nMMMMMMMMMMMMMMMMMMMMMMa i?zOwwzll===lllllztOvzuuyyyyZyZuzwzvrrrO==zlwvzvrO=??====lzl==;+..........\nMMMMMMMMMMMMMMMMMMMMMMMN,`++zttl======lltOrvzuuZyWWZZZXuvvvzwOv=lzltrrrOl=?==lllltttz??+^.........\nMMMMMMMMMMMMMMMMMMMMMMMNgJ ?;1zltz???1OtttwvvuuuZuuXZuzzvrrOOlzzlltrrtttI=llllltrrvrz1=+::+.... . `.\nMMMMMMMMMMMMMMMMMMMMMMMMMM$ ?++1zll???1zOtrrvzzXzzwwwOOvOzzzOrtttOwrrOtlltttttrrvvrrllz?+++;;+.^.. .\nMMMMMMMMMMMMMMMMMMMMMMM   .``;;++=l==?=zzzzzzOzzOwwwwwwZXuuzvrOOvzwvvrttrtrrrrrrvvrtttz??++:;;?++`..\nMMMMMMMMMMMMMMMMMMMM#\"   ....+++?+1=l==lllOrrzzuuuZZZZZuZuzzvvzzzzzzvrrvwrvvrrrvzvrvrtl==?+;;;?+:.^.\nMMMMMMMMMMMMMMMMMB\"!   .......??????=lzzltrrrvzuuuZZuZZuuuuuzuzzzzzvrvzuXzzzrvzvvrvrtttll=???;?;.^..\nMMMMMMMMMM#M9!    .. ..^.....`1z+???==lltrrrvzzuuuuuuuuuZZuZuuuuuzzzzuuuuuzvzzzXvuzvrrttzz==z1+`^...\nMMM\"W\"\"\" ..^```.......^..^..^`+????==l=lztrvzuXZZZZZZyZZZZZZuuuuuzuuuZuZZuuzuzzzuuzvvrrrrOl=lv!^^..^\n.^``^^...^.^^.^......^..^.....?+?=???lttlttwzuXZyyyyyyyyyyZuZuuuuuZZZZuuZuuuuzuuzzzvvrrrrrttZ:^^....\n....`..^.^^.^^^^^^..^..^^....^.?+1===lllttttwXyyyyVVVyVVyVyZZZXZZyZZyZZuuuuzuuuuuzzvrrvvvvzOv^^^....\n";


    /**
     * Build a version object DO NOT call a logger from here
     */
    public Version() {
        readHardWiredConfigInfo();
    }

    /**
     * Read hardwired config info. Cannot be overridden using the normal emissary.config.pkg or emissary.config.dir methods.
     * Does not create a Configurator due to logging restrictions on this method.
     */
    private void readHardWiredConfigInfo() {
        InputStream rstream = null;
        try {
            // This configurator is not overridable by
            // the normal config.pkg or config.dir mechanisms.
            // It also cannot call any method in ConfigUtil that
            // will end up calling a logger.* method, since we
            // are here producing the header for the log file
            String rez = Version.class.getName().replace('.', '/') + ConfigUtil.CONFIG_FILE_ENDING;
            // System.out.println("Reading " + rez);
            rstream = new ResourceReader().getResourceAsStream(rez);
            if (rstream != null) {
                byte[] data = new byte[rstream.available()];
                rstream.read(data, 0, data.length);
                String sdata = new String(data);
                String[] lines = sdata.split("[\r\n]+");
                if (lines != null) {
                    // System.out.println(" Got " + lines.length + " lines");
                    for (int i = 0; i < lines.length; i++) {
                        // System.out.println("Got line " + lines[i]);
                        if (lines[i].startsWith("emissary_version")) {
                            version = getVal(lines[i]);
                        } else if (lines[i].startsWith("emissary_build")) {
                            timestamp = getVal(lines[i]);
                        }
                    }
                }
                // else
                // {
                // System.out.println("No lines to read");
                // }
            }
        } catch (IOException iox) {
            // System.out.println("Bad read: " + iox);
        } finally {
            if (rstream != null) {
                try {
                    rstream.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    /**
     * Do not call a logger method from here!
     */
    private static String getVal(String line) {
        if (line.indexOf(" = ") == -1) {
            return line;
        }
        return line.substring(line.indexOf(" = ") + 3).replaceAll("\"", "");
    }

    @Override
    public String toString() {
        return version + " " + timestamp;
    }

    public String getVersion() {
        return version;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public static void main(String[] args) {
        System.out.println("Emissary version " + new Version());
    }
}
