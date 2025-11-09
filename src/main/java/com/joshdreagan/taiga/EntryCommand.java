package com.joshdreagan.taiga;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;

@TopCommand
@Command(mixinStandardHelpOptions = true, subcommands = { SplitCommand.class, GooglifyCommand.class })
public class EntryCommand {
}
