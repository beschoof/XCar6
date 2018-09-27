mission:

- init
init, t 102, s 26, r 1, v 22

-->
 t 101: Seben
 t 102 Crawler
 s: Lichtschranke: rot per meter (26)
 r, v: 128*r + v => init for gyro (Impulse per grad/sec, ca 150 -> 1;22)

- wait: pause t sekunden
wait, t 3

- move
move, v 3, r 3, t 3, s 3, a 3
v: Geschw., -8..8
r: Kurve, -8..8
t: Zeit sek
s: Weg meter
a: Winkel in Vielfachen zu 30 grad (12 == Vollkreis)
   Richtung ergibt sich aus |r|

Die Parameter t, s, a beschränken den Step. Wenn angegeben, gilt das Minimum.

move, v 1, r -3, t 1, a 24
--> 2 Umdrehungen in mac. 15 sek

move, t 3, v -3
--> 3 Sekunden rückwärts

