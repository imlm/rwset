This is a utility library for finding method dependencies using a
popular static analysis technique called read-write sets analysis
(RWSets). 

Download the source from the following link:

   http://pan.cin.ufpe.br/software/rwsets.tgz

Use the following commands to try it out:

$> ./build
$> ./run

This will show method dependencies across a sample set of cases that
can be found under the folder "src/examples".  The script "build"
compiles the source code and generates a .jar file containing the
application under analysis (currently, those under "src/examples").
Our implementation requires the subject to be packaged this way.  Open
the script "run" to find the arguments that the main class
"depend.Main" takes on input.

The RWSet algorithm computes the transitive closure of field reads
(respectively, field writes) for each application method.  For that,
it first computes direct dependencies (original from parameters and
class fields) for each method and then propagates these direct
dependencies through the edges of the call-graph (we used a 0-CFA
implementation).  The algorithm then computes, for each pair of
methods, the intersection across their read and write sets.  A
read-write dependency is declared if a non-empty intersection is
observed.

To cite RWSets:

@MasterThesis{emami-1993,
  Author="Maryam Emami",
  School="McGill University",
  Year="1993",
  Title="A practical interprocedural alias analysis for an optimizing/parallelizing C compiler"
}

Our implementation builds on the T. J. WAtson Libraries for Analysis
(WALA).  To cite WALA:

@Misc{wala-web,
   Title="T. J. Watson Libraries for Analysis (WALA)",
   Note="\url{wala.sourceforge.net}"
}

Important details:

There are some issues related to the use of WALA in Java versions
>1.7.  In case, you use *nix and you have a version of the JDK prior
to 1.7, please, set your environment to use it with the following
commands.  You will be prompted with a question for which version of
the Java JVM you want to use.

Set the JVM version:

$> sudo update-alternatives --config java

And do the same for the Java compiler:

$> sudo update-alternatives --config javac

enjoy,
 -Marcelo
