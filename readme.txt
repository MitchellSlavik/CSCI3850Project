welcome to our P0 submission's readme!

Authors:
Colin Krist			- Document processing, question answering, documentation
Mitchell Slavik		- Threading / Concurreny data structure research, File I/O, documentation

to check commit contributions please visit: 
https://github.com/MitchellSlavik/CSCI3850Project/commits/master

*************************
Compiling Instructions
1. Make sure you are in the directory of the readme.txt
2. Run: javac ./src/*java
3. Run: unzip ./src/documentset.zip

************
Running Instructions
1. CD into src:  cd ./src
2. Run: java WebSearchProject ./documentset
3. Enjoy!

*************************

Total expected time for completion:

both our local machine on SSD: 7-8 seconds
Loki: 25-35 seconds

*************************

Outputs
[loading bar here]
---------------------------
Beginning analysis for Raw keywords...
Unique keywords: 80393
Top 10 words: a, and, for, in, of, on, said, that, the, to
Top 10 meaningful words (meaningful = len>5): {the=false, a=false, that=false, in=false, and=false, of=false, for=false, to=false, said=false, on=false}
Bottom 10 words: 02:21, 02:22, 02:24, 3631, 3639, reu0070104940814, reu0080074941116, reu0140113950131, wittey, zanattis
---------------------------
Beginning analysis for No Stop Words...
Unique keywords: 80093
Top 10 words: (reuter), government, new, people, police, president, reuters, said, told, united
Top 10 meaningful words (meaningful = len>5): {new=false, reuters=true, government=true, police=true, told=false, united=true, (reuter)=true, said=false, people=true, president=true}
Bottom 10 words: 02:21, 02:22, 02:24, 3631, 3639, reu0070104940814, reu0080074941116, reu0140113950131, wittey, zanattis
---------------------------
Beginning analysis for Stemmed Keywords...
Unique keywords: 60902
Top 10 words: govern, new, offici, peopl, report, reuter, sai, said, state, year
Top 10 meaningful words (meaningful = len>5): {new=false, reuter=true, govern=true, year=false, offici=true, report=true, sai=false, state=false, said=false, peopl=false}
Bottom 10 words: 3631, 3632, 3635, 3639, bcaward, puppetbas, reu0070104940814, reu0080074941116, reu0140113950131, vincentgrenadin
---------------------------
Program complete! (Took 13705ms)