# DepClean <img src="https://github.com/castor-software/depclean/blob/master/logo.svg" align="left" height="135px" alt="DepClean logo"/>

[![Build Status](https://travis-ci.org/castor-software/depclean.svg?branch=master)](https://travis-ci.org/castor-software/depclean)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=alert_status)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=reliability_rating)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=security_rating)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Maven Central](https://img.shields.io/maven-central/v/se.kth.castor/depclean-core.svg)](https://search.maven.org/search?q=g:se.kth.castor%20AND%20a:depclean*)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=bugs)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=code_smells)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=ncloc)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=duplicated_lines_density)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=sqale_index)](https://sonarcloud.io/dashboard?id=castor-software_depclean)

<!--
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=coverage)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
-->

## What is DepClean Visualization?

DepClean Visualization is an interactive website that uses allows a user input an URL of a Java project to scan it with DepClean. The backend service will detect all the unused dependencies declared in the 'pom.xml' file of the project and send it to the front to visualize it in an interactive web site. The goal it to easily show all the unused dependencies and what benefits could a project gain from identifying and deleting them.


# DepClean 
Visualization of the Spoon Maven Dependency tree, with supply chain information (one color = one provider)
<img src="https://github.com/castor-software/depclean/blob/add_visualization/depclean-visualization/img/dependencyTree.jpg" align="left" alt="DepClean visualiztion dependency tree"/>



## License

Distributed under the MIT License. See [LICENSE](https://github.com/castor-software/depclean/blob/master/LICENSE.md) for more information.

## Funding

DepClean is partially funded by the [Wallenberg Autonomous Systems and Software Program (WASP)](https://wasp-sweden.org).

<img src="https://github.com/castor-software/depclean/blob/master/wasp.svg" height="50px" alt="Wallenberg Autonomous Systems and Software Program (WASP)"/>
