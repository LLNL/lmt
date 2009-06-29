#!/bin/bash
# =============================================================================
#  Copyright (c) 2007, The Regents of the University of California.
#  Produced at the Lawrence Livermore National Laboratory.
#  Written by C. Morrone, H. Wartens, P. Spencer, N. O'Neill, J. Long
#  UCRL-CODE-232438.
#  All rights reserved.
#
#  This file is part of LMT-2. For details, see
#  http://sourceforge.net/projects/lmt/.
#
#  Please also read Our Notice and GNU General Public License, available in the
#  COPYING file in the source distribution.
#
#  This program is free software; you can redistribute it and/or modify it
#  under the terms of the GNU General Public License (as published by the Free
#  Software Foundation) version 2, dated June 1991.
#
#  This program is distributed in the hope that it will be useful, but WITHOUT
#  ANY WARRANTY; without even the IMPLIED WARRANTY OF MERCHANTABILITY or
#  FITNESS FOR A PARTICULAR PURPOSE.  See the terms and conditions of the GNU
#  General Public License for more details.
#
#  You should have received a copy of the GNU General Public License along with
#  this program; if not, write to the Free Software Foundation, Inc., 59 Temple
#  Place, Suite 330, Boston, MA 02111-1307 USA
# =============================================================================

if [ -f /etc/SuSE-release ]; then
	if grep -q "VERSION = 9" /etc/SuSE-release; then
		echo SLES9
	elif grep -q "VERSION = 10" /etc/SuSE-release; then
		echo SLES10
	else
		echo "unknown SLES release" >&2
		exit 1
	fi
elif [ -f /etc/fedora-release ]; then
	if grep -q "release 5" /etc/fedora-release; then
		echo FC5
	elif grep -q "release 6" /etc/fedora-release; then
		echo FC6
	elif grep -q "release 7" /etc/fedora-release; then
		echo FC7
	else
		echo "unknown FC release" >&2
		exit 1
	fi
elif [ -f /etc/redhat-release ]; then
	if grep -q "release 4" /etc/redhat-release; then
		echo RHEL4
	elif grep -q "release 5" /etc/redhat-release; then
		echo RHEL5
	else
		echo "unknown RHEL release" >&2
		exit 1
	fi
else
	echo "unknown distro" >&2
	exit 1
fi

exit 0
