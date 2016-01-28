## SparseBitmap
[![][maven img]][maven]
[![][license img]][license]
[![docs-badge][]][docs]



A simple sparse bitmap implementation that has good performance.

Licensing: Apache License, Version 2.0


## Usage:

API: http://www.javadoc.io/doc/com.googlecode.sparsebitmap/SparseBitmap/

```
  public static void main(String[] args) {
    SparseBitmap sp1 = SparseBitmap.bitmapOf(1, 2, 100, 150, 1000, 123456);

    for (int i : sp1)
      System.out.print(i + " ");
    System.out.println();

    SparseBitmap sp2 = SparseBitmap.bitmapOf(1, 2, 3, 1000, 123456, 1234567);

    for (int i : sp2)
      System.out.print(i + " ");
    System.out.println();

    SparseBitmap sand = sp1.and(sp2);

    System.out.println("and:");

    for (int i : sand)
      System.out.print(i + " ");
    System.out.println();
    
    SparseBitmap sor = sp1.or(sp2);
    
    System.out.println("or:");

    for (int i : sor)
      System.out.print(i + " ");
    System.out.println();

  }
```

## Maven support:

You can also specify the dependency in the Maven "pom.xml" file:

```
  <dependencies>
    <dependency>
	<groupId>com.googlecode.sparsebitmap</groupId>
	<artifactId>SparseBitmap</artifactId>
	<version>0.0.4</version>
    </dependency>
  </dependencies>
```

Make sure to replace the version number with the version you actually want.

## Contributors

Daniel Lemire (http://lemire.me/en/) with contributions from
Michal Zerola (https://github.com/zerola)

[maven img]:https://maven-badges.herokuapp.com/maven-central/org.roaringbitmap/RoaringBitmap/badge.svg
[maven]:http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22SparseBitmap%22

[license]:LICENSE
[license img]:https://img.shields.io/badge/License-Apache%202-blue.svg

[docs-badge]:https://img.shields.io/badge/API-docs-blue.svg?style=flat-square
[docs]:http://www.javadoc.io/doc/com.googlecode.sparsebitmap/SparseBitmap
