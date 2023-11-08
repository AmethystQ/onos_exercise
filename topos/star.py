#!/usr/bin/python

import os, sys
from mininet.topo import Topo
from mininet.net import Mininet
from mininet.cli import CLI
from mininet.log import setLogLevel, info, debug
from mininet.node import Host, RemoteController

class TreeTopo( Topo ):
    "Tree topology"

    switch_num = 0
    switch_intf = {}

    def build( self ):
        # Read star.in
        with open('star.in','r') as file:
            nodeRecords = {}
        # Load configuration of Hosts, Switches, and Links
            for i,line in enumerate(file):
                if i==0:
                    lineSplits = line.split(' ')
                    numHost = int(lineSplits[0])
                    numSwitch = int(lineSplits[1])
                    # numLink = int(lineSplits[2])

                    # Add hosts
                    # > self.addHost('h%d' % [HOST NUMBER])    
                    for host in range(numHost):
                        hostName = 'h%d' % (host+1)
                        hostNode = self.addHost(hostName)
                        nodeRecords[hostName] = hostNode

                    # Add switches
                    # > sconfig = {'dpid': "%016x" % [SWITCH NUMBER]}
                    # > self.addSwitch('s%d' % [SWITCH NUMBER], **sconfig)
                    for switch in range(numSwitch):
                        switchName = 's%d' % (switch+1)
                        sconfig = {'dpid': "%016x" % (switch+1)}
                        switchNode = self.addSwitch(switchName, **sconfig)
                        nodeRecords[switchName] = switchNode
                else:
                    lineSplits =line.split(',')
                    nodeOne = lineSplits[0]
                    nodeTwo = lineSplits[1].split('\n')[0]
                    # Add links
                    # > self.addLink([HOST1], [HOST2])
                    self.addLink(nodeRecords[nodeOne],nodeRecords[nodeTwo])      
                    
topos = { 'sdnip' : ( lambda: TreeTopo() ) }

if __name__ == '__main__':
    sys.path.insert(1, '/home/sdn/onos/topos')
    from onosnet import run
    run( TreeTopo() )