/*
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.hfind;

import com.ning.hfind.config.HFindConfig;
import com.ning.hfind.filter.AndOperand;
import com.ning.hfind.filter.Expression;
import com.ning.hfind.filter.Operand;
import com.ning.hfind.filter.OrOperand;
import com.ning.hfind.filter.Primary;
import com.ning.hfind.filter.PrimaryFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.skife.config.ConfigurationObjectFactory;

import java.io.IOException;

public class Find
{
    private static final Options options = new Options();
    private static FileSystem fs;

    private static final String AND = "a";
    private static final String OR = "o";

    private static final int COMMAND_LINE_ERROR = 2;
    private static final int HADOOP_ERROR = 3;

    /**
     * Options which don't make any sense in Hadoop:
     * -xdev The primary shall always evaluate as true; it shall cause find not to continue descending past directories that have a different device ID ( st_dev, see the stat() function defined in the System Interfaces volume of IEEE Std 1003.1-2001). If any -xdev primary is specified, it shall apply to the entire expression even if the -xdev primary would not normally be evaluated.
     * -type only implements 'f' or 'd'
     * -links The primary shall evaluate as true if the file has n links.
     * -atime The primary shall evaluate as true if the file access time subtracted from the initialization time, divided by 86400 (with any remainder discarded), is n.
     * -ctime The primary shall evaluate as true if the time of last change of file status information subtracted from the initialization time, divided by 86400 (with any remainder discarded), is n.
     *
     * TODO: -exec, -ok
     */
    static {
        options.addOption("h", "help", false, "Print this message");

        options.addOption(AND, null, false, "AND operator");
        options.addOption(OR, null, false, "OR operator");

        options.addOption("name", null, true, "True if the last component of the pathname being examined matches pattern");
        options.addOption("nouser", null, false, "True if the file belongs to an unknown user");
        options.addOption("nogroup", null, false, "True if the file belongs to an unknown group");
        options.addOption("prune", null, false, "This primary always evaluates to true. It causes find to not descend into the current file." +
            "Note, the -prune primary has no effect if the -depth option was specified.");
        options.addOption("perm", null, true, "The mode argument is used to represent file mode bits. It shall be identical in format to the symbolic_mode operand described in chmod() , and shall be interpreted as follows. To start, a template shall be assumed with all file mode bits cleared. An op symbol of '+' shall set the appropriate mode bits in the template; '-' shall clear the appropriate bits; '=' shall set the appropriate mode bits, without regard to the contents of process' file mode creation mask. The op symbol of '-' cannot be the first character of mode; this avoids ambiguity with the optional leading hyphen. Since the initial mode is all bits off, there are not any symbolic modes that need to use '-' as the first character.\n" +
            "\n" +
            "If the hyphen is omitted, the primary shall evaluate as true when the file permission bits exactly match the value of the resulting template.\n" +
            "\n" +
            "Otherwise, if mode is prefixed by a hyphen, the primary shall evaluate as true if at least all the bits in the resulting template are set in the file permission bits." +
            "If the hyphen is omitted, the primary shall evaluate as true when the file permission bits exactly match the value of the octal number onum and only the bits corresponding to the octal mask 07777 shall be compared. (See the description of the octal mode in chmod().) Otherwise, if onum is prefixed by a hyphen, the primary shall evaluate as true if at least all of the bits specified in onum that are also set in the octal mask 07777 are set.");
        options.addOption("type", null, true, "The primary shall evaluate as true if the type of the file is arg, where arg is 'd' or 'f' for directory or regular file, respectively");
        options.addOption("user", null, true, "The primary shall evaluate as true if the file belongs to the specified user");
        options.addOption("group", null, true, "The primary shall evaluate as true if the file belongs to the specified group");
        options.addOption("size", null, true, "The primary shall evaluate as true if the file size in bytes, divided by 512 and rounded up to the next integer, is n. If n is followed by the character 'c', the size shall be in bytes");
        options.addOption("mtime", null, true, "The primary shall evaluate as true if the file modification time subtracted from the initialization time, divided by 86400 (with any remainder discarded), is arg");
        options.addOption("print", null, false, "The primary shall always evaluate as true; it shall cause the current pathname to be written to standard output");
        options.addOption("newer", null, true, "The primary shall evaluate as true if the modification time of the current file is more recent than the modification time of the file named by the pathname file");
        options.addOption("depth", null, false, "The primary shall always evaluate as true; it shall cause descent of the directory hierarchy to be done so that all entries in a directory are acted on before the directory itself. If a -depth primary is not specified, all entries in a directory shall be acted on after the directory itself. If any -depth primary is specified, it shall apply to the entire expression even if the -depth primary would not normally be evaluated.");
    }

    public static void usage()
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("hfind [-H | -L] path ... [operand_expression ...]", options);
    }

    private static Configuration configureHDFSAccess(HFindConfig config)
    {
        Configuration conf = new Configuration();

        conf.set("fs.default.name", config.getNamenodeUrl());
        conf.set("hadoop.job.ugi", config.getHadoopUgi());

        return conf;
    }

    private static void connectToHDFS() throws IOException
    {
        HFindConfig hfindConfig = new ConfigurationObjectFactory(System.getProperties()).build(HFindConfig.class);
        Configuration hadoopConfig = configureHDFSAccess(hfindConfig);
        fs = FileSystem.get(hadoopConfig);
    }

    public static void main(String[] args) throws ParseException, IOException
    {
        CommandLineParser parser = new PosixParser();
        CommandLine line = parser.parse(options, args);
        args = line.getArgs();

        if (args.length > 1) {
            // find(1) seems to complain about the first argument only, let's do the same
            System.out.println(String.format("hfind: %s: unknown option", args[1]));
            System.exit(COMMAND_LINE_ERROR);
        }
        if (line.hasOption("help") || args.length == 0) {
            usage();
            return;
        }

        String path = args[0];

        Expression expression = null;
        try {
            expression = buildExpressionFromCommandLine(line.getOptions(), 0);
        }
        catch (IllegalArgumentException e) {
            System.err.println(e);
            System.exit(COMMAND_LINE_ERROR);
        }

        try {
            connectToHDFS();
            expression.run(path, fs);

            System.exit(0);
        }
        catch (IOException e) {
            System.err.println(String.format("Error crawling HDFS: %s", e.getLocalizedMessage()));
            System.exit(HADOOP_ERROR);
        }
    }

    private static Expression buildExpressionFromCommandLine(Option[] options, int index)
    {
        Primary leftPrimary = primaryFromOption(options[index]);
        index++;

        if (index >= options.length) {
            return new Expression(leftPrimary, Primary.ALWAYS_MATCH, new AndOperand());
        }
        Option o = options[index];

        Operand operand;
        if (o.getOpt().equals(OR)) {
            operand = new OrOperand();
            index++;
        }
        else if (o.getOpt().equals(AND)) {
            operand = new AndOperand();
            index++;
        }
        else {
            operand = new AndOperand();
        }

        if (index >= options.length) {
            throw new IllegalArgumentException("Invalid expression");
        }

        return new Expression(leftPrimary, buildExpressionFromCommandLine(options, index), operand);
    }

    private static Primary primaryFromOption(Option o)
    {
        String primary = o.getOpt();
        String argument = o.getValue();
        return PrimaryFactory.getPrimary(primary, argument);
    }
}